package com.lookout.borderpatrol.auth

import com.lookout.borderpatrol.auth.Access.AccessResponse

/**
 * Given an authenticated route/endpoint, this type class will allow us to handle two use cases
 *  - Identified entity is asking for access to service at authenticated route/endpoint
 *  - Entity has not identified itself, must be prompted to identify
 */
trait BorderAuth[C[_]]

/**
 * Service[BorderAuth[Keymaster], Response]
 */
