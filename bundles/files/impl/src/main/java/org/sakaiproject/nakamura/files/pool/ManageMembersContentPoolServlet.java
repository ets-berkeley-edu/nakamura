/**
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
package org.sakaiproject.nakamura.files.pool;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_USER_EDITOR;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_USER_MANAGER;
import static org.sakaiproject.nakamura.api.files.FilesConstants.POOLED_CONTENT_USER_VIEWER;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.commons.json.JSONException;
import org.sakaiproject.nakamura.api.doc.BindingType;
import org.sakaiproject.nakamura.api.doc.ServiceBinding;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.doc.ServiceMethod;
import org.sakaiproject.nakamura.api.doc.ServiceParameter;
import org.sakaiproject.nakamura.api.doc.ServiceResponse;
import org.sakaiproject.nakamura.api.doc.ServiceSelector;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permission;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jcr.RepositoryException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

@SlingServlet(methods = { "GET", "POST" }, resourceTypes = { "sakai/pooled-content" }, selectors = { "members" })
@Properties(value = {
  @Property(name = "service.vendor", value = "The Sakai Foundation"),
  @Property(name = "service.description", value = "Manages the Managers, Editors, and Viewers for pooled content.") })
@ServiceDocumentation(name = "Manage Members Content Pool Servlet", okForVersion = "1.1",
  shortDescription = "List and manage the managers, editors, and viewers for a file in the content pool.",
  description = "List and manage the managers, editors, and viewers for a file in the content pool.",
  bindings = { @ServiceBinding(type = BindingType.TYPE, bindings = { "sakai/pooled-content" },
    selectors = {
      @ServiceSelector(name = "members", description = "Binds to the selector members."),
      @ServiceSelector(name = "detailed", description = "(optional) Provides more detailed profile information."),
      @ServiceSelector(name = "tidy", description = "(optional) Provides formatted JSON output.")
    })
  },
  methods = {
    @ServiceMethod(name = "GET",
      description = {
        "Retrieves a list of managers, editors, and viewers for this pooled content item.",
        "<pre>curl http://localhost:8080/p/hESoXumAT.members.tidy.json</pre>",
        "<pre>{\n" +
          "    \"managers\": [{\n" +
          "        \"hash\": \"suzy\",\n" +
          "        \"basic\": {\n" +
          "            \"access\": \"everybody\",\n" +
          "            \"elements\": {\n" +
          "                \"picture\": {\n" +
          "                    \"value\": \"{\\\"name\\\":\\\"256x256_tmp1309269939493.jpg\\\",\\\"_name\\\":\\\"tmp1309269939493.jpg\\\",\\\"_charset_\\\":\\\"utf-8\\\",\\\"selectedx1\\\":0,\\\"selectedy1\\\":3,\\\"selectedx2\\\":85,\\\"selectedy2\\\":88}\"\n" +
          "                },\n" +
          "                \"lastName\": {\n" +
          "                    \"value\": \"Queue\"\n" +
          "                },\n" +
          "                \"email\": {\n" +
          "                    \"value\": \"suzy@aeroplanesoftware.com\"\n" +
          "                },\n" +
          "                \"firstName\": {\n" +
          "                    \"value\": \"Suzy\"\n" +
          "                }\n" +
          "            }\n" +
          "        },\n" +
          "        \"rep:userId\": \"suzy\",\n" +
          "        \"userid\": \"suzy\",\n" +
          "        \"counts\": {\n" +
          "            \"contactsCount\": 0,\n" +
          "            \"membershipsCount\": 0,\n" +
          "            \"contentCount\": 3,\n" +
          "            \"countLastUpdate\": 1309287542572\n" +
          "        },\n" +
          "        \"sakai:excludeSearch\": false\n" +
          "    }],\n" +
          "    \"editors\": [{\n" +
          "        \"hash\": \"alice.b.toklas\",\n" +
          "        \"basic\": {\n" +
          "            \"access\": \"everybody\",\n" +
          "            \"elements\": {\n" +
          "                \"picture\": {\n" +
          "                    \"value\": \"{\\\"name\\\":\\\"256x256_tmp1309269939493.jpg\\\",\\\"_name\\\":\\\"tmp1309269939493.jpg\\\",\\\"_charset_\\\":\\\"utf-8\\\",\\\"selectedx1\\\":0,\\\"selectedy1\\\":3,\\\"selectedx2\\\":85,\\\"selectedy2\\\":88}\"\n" +
          "                },\n" +
          "                \"lastName\": {\n" +
          "                    \"value\": \"Toklas\"\n" +
          "                },\n" +
          "                \"email\": {\n" +
          "                    \"value\": \"aliceb@toklas.com\"\n" +
          "                },\n" +
          "                \"firstName\": {\n" +
          "                    \"value\": \"Alice\"\n" +
          "                }\n" +
          "            }\n" +
          "        },\n" +
          "        \"rep:userId\": \"aliceb\",\n" +
          "        \"userid\": \"aliceb\",\n" +
          "        \"counts\": {\n" +
          "            \"contactsCount\": 0,\n" +
          "            \"membershipsCount\": 0,\n" +
          "            \"contentCount\": 3,\n" +
          "            \"countLastUpdate\": 1309287542572\n" +
          "        },\n" +
          "        \"sakai:excludeSearch\": false\n" +
          "    }],\n" +
          "    \"viewers\": [{\n" +
          "        \"basic\": {\n" +
          "            \"access\": \"everybody\",\n" +
          "            \"elements\": {\n" +
          "                \"lastName\": {\n" +
          "                    \"value\": \"User\"\n" +
          "                },\n" +
          "                \"email\": {\n" +
          "                    \"value\": \"anon@sakai.invalid\"\n" +
          "                },\n" +
          "                \"firstName\": {\n" +
          "                    \"value\": \"Anonymous\"\n" +
          "                }\n" +
          "            }\n" +
          "        },\n" +
          "        \"rep:userId\": \"anonymous\"\n" +
          "    },\n" +
          "    {\n" +
          "        \"sakai:category\": null,\n" +
          "        \"sakai:group-description\": null,\n" +
          "        \"sakai:group-id\": \"everyone\",\n" +
          "        \"createdBy\": null,\n" +
          "        \"lastModified\": null,\n" +
          "        \"sakai:group-title\": null,\n" +
          "        \"created\": null,\n" +
          "        \"basic\": {\n" +
          "            \"access\": \"everybody\",\n" +
          "            \"elements\": {}\n" +
          "        },\n" +
          "        \"lastModifiedBy\": null,\n" +
          "        \"groupid\": \"everyone\",\n" +
          "        \"counts\": {},\n" +
          "        \"sakai:excludeSearch\": false\n" +
          "    }]\n" +
          "}</pre>"
      },
      response = {
        @ServiceResponse(code = 200, description = "All processing finished successfully.  Output is in the JSON format."),
        @ServiceResponse(code = 500, description = "Any exceptions encountered during processing.")
      }),
    @ServiceMethod(name = "POST", description = "Manipulate the member list for a pooled content item.",
      parameters = {
        @ServiceParameter(name = ":manager", description = "Set the managers on the ACL of a file."),
        @ServiceParameter(name = ":editor", description = "Set the editors on the ACL of a file."),
        @ServiceParameter(name = ":viewer", description = "Set the viewers on the ACL of a file.")
      },
      response = {
        @ServiceResponse(code = 200, description = "All processing finished successfully."),
        @ServiceResponse(code = 401, description = "POST by anonymous user, or current user doesn't have permission to update this content."),
        @ServiceResponse(code = 500, description = "Any exceptions encountered during processing.")
      })
  })
  public class ManageMembersContentPoolServlet extends SlingAllMethodsServlet {

  private static final long serialVersionUID = 3385014961034481906L;
  private static final Logger LOGGER = LoggerFactory
      .getLogger(ManageMembersContentPoolServlet.class);

  private static final Permission PERMISSION_EDITOR = Permissions.CAN_READ.combine(Permissions.CAN_WRITE);

  @Reference
  protected transient ProfileService profileService;
  @Reference
  protected transient BasicUserInfoService basicUserInfoService;

  /**
   * Retrieves the list of members.
   *
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.servlets.SlingSafeMethodsServlet#doGet(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    try {
      // Get hold of the actual file.
      Resource resource = request.getResource();
     javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(javax.jcr.Session.class);
      Session session = resource.adaptTo(Session.class);

      AuthorizableManager am = session.getAuthorizableManager();
      AccessControlManager acm = session.getAccessControlManager();
      Content node = resource.adaptTo(Content.class);
      Authorizable thisUser = am.findAuthorizable(session.getUserId());

      if (!acm.can(thisUser, Security.ZONE_CONTENT, resource.getPath(), Permissions.CAN_READ)) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      Map<String, Object> properties = node.getProperties();
      String[] managers = (String[]) properties
          .get(POOLED_CONTENT_USER_MANAGER);
      String[] editors = (String[]) properties
          .get(POOLED_CONTENT_USER_EDITOR);
      String[] viewers = (String[]) properties
          .get(POOLED_CONTENT_USER_VIEWER);


      boolean detailed = false;
      boolean tidy = false;
      for (String selector : request.getRequestPathInfo().getSelectors()) {
        if ("detailed".equals(selector)) {
          detailed = true;
        } else if ("tidy".equals(selector)) {
          tidy = true;
        }
      }

      // Loop over the sets and output it.
      ExtendedJSONWriter writer = new ExtendedJSONWriter(response.getWriter());
      writer.setTidy(tidy);
      writer.object();
      writer.key("managers");
      writer.array();
      for (String manager : StorageClientUtils.nonNullStringArray(managers)) {
        try {
          writeProfileMap(jcrSession, am, writer, manager, detailed);
        } catch (AccessDeniedException e) {
          LOGGER.debug("Skipping private manager [{}]", manager);
        }
      }
      writer.endArray();
      writer.key("editors");
      writer.array();
      for (String editor : StorageClientUtils.nonNullStringArray(editors)) {
        try {
          writeProfileMap(jcrSession, am, writer, editor, detailed);
        } catch (AccessDeniedException e) {
          LOGGER.debug("Skipping private editor [{}]", editor);
        }
      }
      writer.endArray();
      writer.key("viewers");
      writer.array();
      for (String viewer : StorageClientUtils.nonNullStringArray(viewers)) {
        try {
          writeProfileMap(jcrSession, am, writer, viewer, detailed);
        } catch (AccessDeniedException e) {
          LOGGER.debug("Skipping private viewer [{}]", viewer);
        }
      }
      writer.endArray();
      writer.endObject();
    } catch (JSONException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
      LOGGER.error(e.getMessage(), e);
    } catch (StorageClientException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
      LOGGER.error(e.getMessage(), e);
    } catch (RepositoryException e) {
      response.sendError(SC_INTERNAL_SERVER_ERROR, "Failed to generate proper JSON.");
      LOGGER.error(e.getMessage(), e);
    }

  }

  private void writeProfileMap(javax.jcr.Session jcrSession, AuthorizableManager um,
      ExtendedJSONWriter writer, String user, boolean detailed)
      throws JSONException, AccessDeniedException, StorageClientException, RepositoryException {
    Authorizable au = um.findAuthorizable(user);
    if (au != null) {
      ValueMap profileMap;
      if (detailed) {
        profileMap = profileService.getProfileMap(au, jcrSession);
      } else {
        profileMap = new ValueMapDecorator(basicUserInfoService.getProperties(au));
      }
      if (profileMap != null) {
        writer.valueMap(profileMap);
      }
    } else {
      writer.object();
      writer.key("userid");
      writer.value(user);
      writer.endObject();
    }
  }

  /**
   * Manipulate the member list for this file.
   *
   * {@inheritDoc}
   *
   * @see org.apache.sling.api.servlets.SlingAllMethodsServlet#doPost(org.apache.sling.api.SlingHttpServletRequest,
   *      org.apache.sling.api.SlingHttpServletResponse)
   */
  @Override
  protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
      throws ServletException, IOException {
    // fail if anonymous
    String remoteUser = request.getRemoteUser();
    if (User.ANON_USER.equals(remoteUser)) {
      response.sendError(SC_FORBIDDEN, "Anonymous users cannot update content members.");
      return;
    }
    Session session = null;
    boolean releaseSession = false;
    try {
      Resource resource = request.getResource();
      session = resource.adaptTo(Session.class);
      Content pooledContent = resource.adaptTo(Content.class);
      AccessControlManager accessControlManager = session.getAccessControlManager();
      AuthorizableManager authorizableManager = session.getAuthorizableManager();
      User thisUser = authorizableManager.getUser();
      if (!accessControlManager.can(thisUser, Security.ZONE_CONTENT, pooledContent.getPath(), Permissions.CAN_READ)) {
        response.sendError(SC_FORBIDDEN, "Insufficient permission to read this content.");
      }

      Map<String, Object> properties = pooledContent.getProperties();
      String[] managers = StorageClientUtils.nonNullStringArray((String[]) properties
          .get(POOLED_CONTENT_USER_MANAGER));
      String[] editors = StorageClientUtils.nonNullStringArray((String[]) properties
          .get(POOLED_CONTENT_USER_EDITOR));
      String[] viewers = StorageClientUtils.nonNullStringArray((String[]) properties
          .get(POOLED_CONTENT_USER_VIEWER));

      Set<String> managerSet = Sets.newHashSet(managers);
      Set<String> editorSet = Sets.newHashSet(editors);
      Set<String> viewerSet = Sets.newHashSet(viewers);

      List<String> removeViewers = Arrays.asList(StorageClientUtils.nonNullStringArray(request.getParameterValues(":viewer@Delete")));
      List<String> removeManagers = Arrays.asList(StorageClientUtils.nonNullStringArray(request.getParameterValues(":manager@Delete")));
      List<String> removeEditors = Arrays.asList(StorageClientUtils.nonNullStringArray(request.getParameterValues(":editor@Delete")));
      List<String> addViewers = Arrays.asList(StorageClientUtils.nonNullStringArray(request.getParameterValues(":viewer")));
      List<String> addManagers = Arrays.asList(StorageClientUtils.nonNullStringArray(request.getParameterValues(":manager")));
      List<String> addEditors = Arrays.asList(StorageClientUtils.nonNullStringArray(request.getParameterValues(":editor")));

      if (!accessControlManager.can(thisUser, Security.ZONE_CONTENT, pooledContent.getPath(), Permissions.CAN_WRITE)) {
        if (!addManagers.isEmpty()) {
          response.sendError(SC_FORBIDDEN, "Non-managers may not add managers to content.");
          return;
        }

        for (String name : removeManagers) {
          // asking to remove managers who don't exist is harmless
          if (managerSet.contains(name)) {
            response.sendError(SC_FORBIDDEN, "Non-managers may not remove managers from content.");
            return;
          }
        }

        if (addViewers.contains(User.ANON_USER) || addViewers.contains(Group.EVERYONE)) {
          response.sendError(SC_FORBIDDEN, "Non-managers may not add 'anonymous' or 'everyone' as viewers.");
          return;
        }

        if (addEditors.contains(User.ANON_USER) || addEditors.contains(Group.EVERYONE)) {
          response.sendError(SC_FORBIDDEN, "Non-managers may not add 'anonymous' or 'everyone' as editors.");
          return;
        }

        for (String name : removeViewers) {
          if (!thisUser.getId().equals(name)) {
            Authorizable viewer = authorizableManager.findAuthorizable(name);
            if (viewer != null && !accessControlManager.can(thisUser, Security.ZONE_AUTHORIZABLES, name, Permissions.CAN_WRITE)) {
              response.sendError(SC_FORBIDDEN, "Non-managers may not remove any viewer other than themselves or a group which they manage.");
            }
          }
        }

        // the request has passed all the rules that govern non-manager users
        // so we'll grant an administrative session
        session = session.getRepository().loginAdministrative();
        releaseSession = true;
      }
      List<AclModification> aclModifications = Lists.newArrayList();

      for (String addManager : addManagers) {
        if ((addManager.length() > 0) && !managerSet.contains(addManager)) {
          managerSet.add(addManager);
          AclModification.addAcl(true, Permissions.CAN_MANAGE, addManager,
              aclModifications);
        }
      }

      for (String removeManager : removeManagers) {
        if ((removeManager.length() > 0) && managerSet.contains(removeManager)) {
          managerSet.remove(removeManager);
          AclModification.removeAcl(true, Permissions.CAN_MANAGE, removeManager,
              aclModifications);
        }
      }

      for (String addEditor : addEditors) {
        if ((addEditor.length() > 0) && !editorSet.contains(addEditor)) {
          editorSet.add(addEditor);
          AclModification.addAcl(true, PERMISSION_EDITOR, addEditor,
              aclModifications);
        }
      }

      for (String removeEditor : removeEditors) {
        if ((removeEditor.length() > 0) && editorSet.contains(removeEditor)) {
          editorSet.remove(removeEditor);
          AclModification.removeAcl(true, PERMISSION_EDITOR, removeEditor,
              aclModifications);
        }
      }

      for (String addViewer : addViewers) {
        if ((addViewer.length() > 0) && !viewerSet.contains(addViewer)) {
          viewerSet.add(addViewer);
          AclModification.addAcl(true, Permissions.CAN_READ, addViewer, aclModifications);
        }
      }

      for (String removeViewer : removeViewers) {
        removeViewer = removeViewer.trim();
        if ((removeViewer.length() > 0) && viewerSet.contains(removeViewer)) {
          viewerSet.remove(removeViewer);
          if (!managerSet.contains(removeViewer)) {
            AclModification.removeAcl(true, Permissions.CAN_READ, removeViewer,
                aclModifications);
          }
        }
      }

      updateContentMembers(session, pooledContent, viewerSet,  managerSet, editorSet);
      updateContentAccess(session, pooledContent, aclModifications);

      response.setStatus(SC_OK);

    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage());
      response.sendError(SC_INTERNAL_SERVER_ERROR, "StorageClientException: " + e.getLocalizedMessage());
    } catch (AccessDeniedException e) {
      response.sendError(SC_FORBIDDEN, "Insufficient permission to update content members at " + request.getRequestURI());
    } finally {
      if (session != null && releaseSession) {
        try {
          session.logout();
        } catch (ClientPoolException e) {
          LOGGER.error(e.getMessage());
        }
      }
    }
  }

  private void updateContentMembers(Session session, Content content, Set<String> viewerSet, Set<String> managerSet, Set<String> editorSet)
          throws StorageClientException, AccessDeniedException {
    content.setProperty(POOLED_CONTENT_USER_VIEWER,
        viewerSet.toArray(new String[viewerSet.size()]));
    content.setProperty(POOLED_CONTENT_USER_MANAGER,
        managerSet.toArray(new String[managerSet.size()]));
    content.setProperty(POOLED_CONTENT_USER_EDITOR,
        editorSet.toArray(new String[editorSet.size()]));
    LOGGER.debug("Set Managers to {}",Arrays.toString(managerSet.toArray(new String[managerSet.size()])));
    LOGGER.debug("Set Editors to {}",Arrays.toString(editorSet.toArray(new String[editorSet.size()])));
    LOGGER.debug("Set Viewers to {}",Arrays.toString(viewerSet.toArray(new String[managerSet.size()])));
    session.getContentManager().update(content);
  }

  private void updateContentAccess(Session session, Content content, List<AclModification> aclModifications) throws StorageClientException, AccessDeniedException {
    LOGGER.debug("ACL Modifications {}",Arrays.toString(aclModifications.toArray(new AclModification[aclModifications.size()])));
    session.getAccessControlManager().setAcl(Security.ZONE_CONTENT, content.getPath(),
      aclModifications.toArray(new AclModification[aclModifications.size()]));
  }

}
