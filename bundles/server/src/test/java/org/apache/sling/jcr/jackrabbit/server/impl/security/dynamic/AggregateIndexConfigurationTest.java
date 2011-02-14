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
package org.apache.sling.jcr.jackrabbit.server.impl.security.dynamic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.NodeTypeTemplate;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.junit.Assert;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;

/**
 * Validates that its possible to aggregate content
 */
public class AggregateIndexConfigurationTest {

  
  private static final String FILE_CONTENT = "The Quick Brown Fox Jumped over the Gate";

  @Test
	public void testAggregateIndex() throws IOException, RepositoryException,
			InterruptedException, ClientPoolException,
			StorageClientException, AccessDeniedException,
			ClassNotFoundException {
    RepositoryBase repositoryBase = RepositoryBaseTest.getRepositoryBase();
    Repository repository = repositoryBase.getRepository();
    Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
    NodeTypeManager ntm = session.getWorkspace().getNodeTypeManager();
    
    NodeTypeTemplate ntt = ntm.createNodeTypeTemplate();
    ntt.setDeclaredSuperTypeNames(new String[]{"nt:unstructured"});
    ntt.setName("sakai:user-home");
    
    ntm.registerNodeType(ntt, true);
    
    
    
   
    
    
    
    
    
    Node rootNode = session.getRootNode();
    Node homeNode = rootNode.addNode("iebhome", "sakai:user-home");
    homeNode.setProperty("sling:resourceType", "sakai/user-home");
    Node publicNode = homeNode.addNode("public", "nt:unstructured");
    Node publicSub = publicNode.addNode("sub", "nt:unstructured");
    publicSub.setProperty("test", "The brown fox");
    Node publicSub2 = publicNode.addNode("sub2", "nt:unstructured");
    publicSub2.setProperty("test", "The brown fox");
    Node childFileNode = publicSub.addNode("childNode", "nt:file");
    Node resourceNode =  childFileNode.addNode("jcr:content","nt:resource");
    ValueFactory valueFactory = session.getValueFactory();
    InputStream fileStream = new ByteArrayInputStream(FILE_CONTENT.getBytes("UTF-8"));
    Binary binary = valueFactory.createBinary(fileStream);
    resourceNode.setProperty("jcr:data", binary);
    session.save();
    session.logout();
    
    Thread.sleep(100);

    session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
    Node n = session.getNode("/iebhome/public/sub");
    Assert.assertEquals("The brown fox", n.getProperty("test").getString());

    
    QueryManager qm = session.getWorkspace().getQueryManager();
    {
    Query q = qm.createQuery("//*[@sling:resourceType = 'sakai/user-home' and jcr:contains(.,'brown')]", Query.XPATH);
    QueryResult qr = q.execute();
    NodeIterator nodeIterator = qr.getNodes();
    Assert.assertTrue(nodeIterator.hasNext());
    Assert.assertEquals("/iebhome", nodeIterator.nextNode().getPath());
    Assert.assertFalse(nodeIterator.hasNext());
    }
    {
    Query q = qm.createQuery("//*[@sling:resourceType = 'sakai/user-home']", Query.XPATH);
    QueryResult qr = q.execute();
    NodeIterator nodeIterator = qr.getNodes();
    Assert.assertTrue(nodeIterator.hasNext());
    Assert.assertEquals("/iebhome", nodeIterator.nextNode().getPath());
    Assert.assertFalse(nodeIterator.hasNext());
    }
    {
      Query q = qm.createQuery("//*[jcr:contains(.,'brown')]", Query.XPATH);
      QueryResult qr = q.execute();
      NodeIterator nodeIterator = qr.getNodes();
      List<String> paths = new ArrayList<String>();
      while (nodeIterator.hasNext()) {
        paths.add(nodeIterator.nextNode().getPath());
      }
      
      Assert.assertTrue(paths.contains("/iebhome"));
      Assert.assertTrue(paths.contains("/iebhome/public/sub"));
      Assert.assertTrue(paths.contains("/iebhome/public/sub2"));
      }
    session.logout();
  }
}
