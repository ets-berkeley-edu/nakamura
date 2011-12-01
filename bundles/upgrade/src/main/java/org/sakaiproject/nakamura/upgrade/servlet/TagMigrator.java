/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.sakaiproject.nakamura.upgrade.servlet;

import com.google.common.collect.ImmutableMap;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

@Component
@Service(value = TagMigrator.class)
public class TagMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(TagMigrator.class);

  private static final String SYSTEM_LOG_PATH = "system/v1.1.1-tagmigratorrunlog";

  @Reference
  private SlingRepository slingRepository;

  @Reference
  private Repository sparseRepository;

  @Reference
  private SolrSearchServiceFactory searchServiceFactory;

  public void migrate(final SlingHttpServletRequest request, final SlingHttpServletResponse response, boolean dryRun, boolean reindexAll)
          throws SolrSearchException, RepositoryException, StorageClientException, AccessDeniedException {

    org.sakaiproject.nakamura.api.lite.Session sparseSession = null;
    Session jcrSession = null;

    try {
      sparseSession = this.sparseRepository.loginAdministrative();
      jcrSession = this.slingRepository.loginAdministrative("default");

      if (needsMigration(sparseSession) || reindexAll) {
        SparseUpgradeServlet.writeToResponse("Migrating tags from JCR to Sparse...", response);
        SolrSearchResultSet taggedDocuments = getTaggedDocuments(request);
        Set<String> allTags = getUniqueTags(taggedDocuments);
        ensureTagsExistInJCR(allTags, jcrSession, dryRun);
        saveTagsInSparse(jcrSession, sparseSession, dryRun);
      } else {
        SparseUpgradeServlet.writeToResponse("Tag Migrator already ran on this system, skipping migration", response);
      }

    } finally {
      if (sparseSession != null) {
        sparseSession.logout();
      }
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }
  }

  private boolean needsMigration(org.sakaiproject.nakamura.api.lite.Session session)
          throws StorageClientException, AccessDeniedException {
    ContentManager cm = session.getContentManager();
    return !cm.exists(SYSTEM_LOG_PATH);
  }

  private SolrSearchResultSet getTaggedDocuments(SlingHttpServletRequest request) throws SolrSearchException {
    Query query = new Query("tag:*");
    SolrSearchResultSet results = searchServiceFactory.getSearchResultSet(request, query);
    LOGGER.info("Got " + results.getSize() + " tagged documents from solr index");
    return results;
  }

  private Set<String> getUniqueTags(SolrSearchResultSet taggedDocuments) {
    Set<String> allTags = new HashSet<String>();
    Iterator<Result> iterator = taggedDocuments.getResultSetIterator();
    while (iterator.hasNext()) {
      Map<String, Collection<Object>> props = iterator.next().getProperties();
      Collection<Object> tags = props.get("tag");
      if (tags != null) {
        for (Object o : tags) {
          if (o instanceof String) {
            String tag = (String) o;
            allTags.add(tag);
          }
        }
      }
    }
    LOGGER.info("We have " + allTags.size() + " unique tags represented in Solr indexes: " + allTags);
    return allTags;
  }

  private void ensureTagsExistInJCR(Set<String> allTags, Session session, boolean dryRun) throws RepositoryException {
    Node jcrTags = session.getNode("/tags");
    if (jcrTags == null) {
      jcrTags = session.getRootNode().addNode("tags");
    }
    for (String tag : allTags) {
      if (!jcrTags.hasNode(tag)) {
        LOGGER.info("JCR lacks the tag " + tag + ", creating it...");
        if (!dryRun) {
          jcrTags.addNode(tag);
        }
      }
    }
    if (!dryRun) {
      session.save();
    }

  }

  private void saveTagsInSparse(Session jcrSession, org.sakaiproject.nakamura.api.lite.Session sparseSession, boolean dryRun)
          throws StorageClientException, AccessDeniedException, RepositoryException {

    ContentManager cm = sparseSession.getContentManager();

    // loop over tags in jcr and save them to sparse
    Node jcrTags = jcrSession.getNode("/tags");
    if (jcrTags != null) {
      NodeIterator iterator = jcrTags.getNodes();
      while (iterator.hasNext()) {
        Node node = iterator.nextNode();

        // make sure we only recreate actual sakai:tag nodes
        if (node.hasProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY)) {
          if (node.getProperty(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY).
                  getString().equals(FilesConstants.RT_SAKAI_TAG)) {
            String tag = node.getName();
            String tagPath = "/tags/" + tag;
            if (cm.exists(tagPath)) {
              LOGGER.info("Tag " + tag + " already exists in sparse, skipping");
            } else {
              LOGGER.info("Tag " + tag + " does not yet exist in sparse, creating");
              Content content = new Content(tagPath,
                      ImmutableMap.of(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY,
                              (Object) FilesConstants.RT_SAKAI_TAG));
              content.setProperty(FilesConstants.SAKAI_TAG_NAME, tag);
              if (!dryRun) {
                cm.update(content);
              }
            }
          }
        }
      }
    }

    // log when the migrator ran if this is for real
    if (!dryRun) {
      Content systemLog = new Content(SYSTEM_LOG_PATH,
              ImmutableMap.<String, Object>of("migrationTime", System.currentTimeMillis()));
      cm.update(systemLog);
    }

  }

}
