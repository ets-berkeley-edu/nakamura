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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import org.sakaiproject.nakamura.api.lite.SessionAdaptable;

import javax.jcr.Session;

/**
 * This interface is a mockable stub that represents both a JCR Session, and something that is
 * adaptable to a Nakamura session. This is handy for mocking the path for adapting a Sling
 * request to a pre-determined Nakamura lite Session.
 */
public interface MockableJcrSessionSessionAdaptable extends Session, SessionAdaptable {

}