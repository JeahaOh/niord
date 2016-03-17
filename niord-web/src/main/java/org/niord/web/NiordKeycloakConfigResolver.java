package org.niord.web;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.authentication.ClientCredentialsProvider;
import org.keycloak.adapters.spi.HttpFacade;
import org.keycloak.common.enums.RelativeUrlsUsed;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.enums.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the keycloak.json to use for a given request
 *
 * Currently, "/keycloak.json" will be loaded from class path.
 *
 * IMPORTANT: In order to handle relative auth-server-url's, various functions and helper classes
 * has been copied from Keycloak AdapterDeploymentContext class.
 * This should be kept up-to-date with future versions of Keycloak.
 */
public class NiordKeycloakConfigResolver implements KeycloakConfigResolver {

    private final Map<String, KeycloakDeployment> cache = new ConcurrentHashMap<>();

    private final Logger log = LoggerFactory.getLogger(NiordKeycloakConfigResolver.class);

    /** {@inheritDoc} */
    @Override
    public KeycloakDeployment resolve(HttpFacade.Request request) {

        String client = resolveClient(request);

        KeycloakDeployment deployment = cache.get(client);
        if (null == deployment) {

            // not found on the simple cache, try to load it from the file system
            String kcFile = StringUtils.isBlank(client) ? "keycloak.json" : client + "-keycloak.json";
            InputStream is = getClass().getResourceAsStream("/" + kcFile);
            if (is == null) {
                throw new IllegalStateException("Not able to find the file /" + kcFile);
            }
            deployment = KeycloakDeploymentBuilder.build(is);
            cache.put(client, deployment);
        }


        if (deployment.getAuthServerBaseUrl() == null) {
            return deployment;
        }
        return resolveUrls(deployment, request);
    }

    /** Resolves the client ID from the request */
    @SuppressWarnings("unused")
    private String resolveClient(HttpFacade.Request request) {
        // TODO: Implement
        return "";
    }


    /**
     * IMPORTANT: This function has been copied (and modified) from the Keycloak AdapterDeploymentContext class.
     * It should be kept up-to-date with future versions of Keycloak.
     */
    protected KeycloakDeployment resolveUrls(KeycloakDeployment deployment, HttpFacade.Request facadeRequest) {
        if (deployment.getRelativeUrls() == RelativeUrlsUsed.NEVER) {
            // Absolute URI are already set to everything
            return deployment;
        } else {
            DeploymentDelegate delegate = new DeploymentDelegate(deployment);
            delegate.setAuthServerBaseUrl(getBaseBuilder(deployment, facadeRequest).build().toString());
            return delegate;
        }
    }


    /**
     * IMPORTANT: This function has been copied (and modified) from the Keycloak AdapterDeploymentContext class.
     * It should be kept up-to-date with future versions of Keycloak.
     */
    protected KeycloakUriBuilder getBaseBuilder(KeycloakDeployment deployment, HttpFacade.Request facadeRequest) {
        String base = deployment.getAuthServerBaseUrl();
        KeycloakUriBuilder builder = KeycloakUriBuilder.fromUri(base);
        URI request = URI.create(facadeRequest.getURI());
        String scheme = request.getScheme();
        if (deployment.getSslRequired().isRequired(facadeRequest.getRemoteAddr())) {
            scheme = "https";
            if (!request.getScheme().equals(scheme) && request.getPort() != -1) {
                log.error("request scheme: " + request.getScheme() + " ssl required");
                throw new RuntimeException("Can't resolve relative url from adapter config.");
            }
        }
        builder.scheme(scheme);
        builder.host(request.getHost());
        if (request.getPort() != -1) {
            builder.port(request.getPort());
        }
        return builder;
    }


    /**
     * IMPORTANT: This class has been copied from the Keycloak AdapterDeploymentContext class.
     * It should be kept up-to-date with future versions of Keycloak.
     *
     *
     * This delegate is used to store temporary, per-request metadata like request resolved URLs.
     * Ever method is delegated except URL get methods and isConfigured()
     *
     */
    protected static class DeploymentDelegate extends KeycloakDeployment {
        protected KeycloakDeployment delegate;

        public DeploymentDelegate(KeycloakDeployment delegate) {
            this.delegate = delegate;
        }

        public void setAuthServerBaseUrl(String authServerBaseUrl) {
            this.authServerBaseUrl = authServerBaseUrl;
            KeycloakUriBuilder serverBuilder = KeycloakUriBuilder.fromUri(authServerBaseUrl);
            resolveBrowserUrls(serverBuilder);

            if (delegate.getRelativeUrls() == RelativeUrlsUsed.ALL_REQUESTS) {
                resolveNonBrowserUrls(serverBuilder);
            }
        }

        @Override
        public RelativeUrlsUsed getRelativeUrls() {
            return delegate.getRelativeUrls();
        }

        @Override
        public String getRealmInfoUrl() {
            return (this.realmInfoUrl != null) ? this.realmInfoUrl : delegate.getRealmInfoUrl();
        }

        @Override
        public String getTokenUrl() {
            return (this.tokenUrl != null) ? this.tokenUrl : delegate.getTokenUrl();
        }

        @Override
        public KeycloakUriBuilder getLogoutUrl() {
            return (this.logoutUrl != null) ? this.logoutUrl : delegate.getLogoutUrl();
        }

        @Override
        public String getAccountUrl() {
            return (this.accountUrl != null) ? this.accountUrl : delegate.getAccountUrl();
        }

        @Override
        public String getRegisterNodeUrl() {
            return (this.registerNodeUrl != null) ? this.registerNodeUrl : delegate.getRegisterNodeUrl();
        }

        @Override
        public String getUnregisterNodeUrl() {
            return (this.unregisterNodeUrl != null) ? this.unregisterNodeUrl : delegate.getUnregisterNodeUrl();
        }

        @Override
        public String getResourceName() {
            return delegate.getResourceName();
        }

        @Override
        public String getRealm() {
            return delegate.getRealm();
        }

        @Override
        public void setRealm(String realm) {
            delegate.setRealm(realm);
        }

        @Override
        public PublicKey getRealmKey() {
            return delegate.getRealmKey();
        }

        @Override
        public void setRealmKey(PublicKey realmKey) {
            delegate.setRealmKey(realmKey);
        }

        @Override
        public void setResourceName(String resourceName) {
            delegate.setResourceName(resourceName);
        }

        @Override
        public boolean isBearerOnly() {
            return delegate.isBearerOnly();
        }

        @Override
        public void setBearerOnly(boolean bearerOnly) {
            delegate.setBearerOnly(bearerOnly);
        }

        @Override
        public boolean isEnableBasicAuth() {
            return delegate.isEnableBasicAuth();
        }

        @Override
        public void setEnableBasicAuth(boolean enableBasicAuth) {
            delegate.setEnableBasicAuth(enableBasicAuth);
        }

        @Override
        public boolean isPublicClient() {
            return delegate.isPublicClient();
        }

        @Override
        public void setPublicClient(boolean publicClient) {
            delegate.setPublicClient(publicClient);
        }

        @Override
        public Map<String, Object> getResourceCredentials() {
            return delegate.getResourceCredentials();
        }

        @Override
        public void setResourceCredentials(Map<String, Object> resourceCredentials) {
            delegate.setResourceCredentials(resourceCredentials);
        }

        @Override
        public void setClientAuthenticator(ClientCredentialsProvider clientAuthenticator) {
            delegate.setClientAuthenticator(clientAuthenticator);
        }

        @Override
        public ClientCredentialsProvider getClientAuthenticator() {
            return delegate.getClientAuthenticator();
        }

        @Override
        public HttpClient getClient() {
            return delegate.getClient();
        }

        @Override
        public void setClient(HttpClient client) {
            delegate.setClient(client);
        }

        @Override
        public String getScope() {
            return delegate.getScope();
        }

        @Override
        public void setScope(String scope) {
            delegate.setScope(scope);
        }

        @Override
        public SslRequired getSslRequired() {
            return delegate.getSslRequired();
        }

        @Override
        public void setSslRequired(SslRequired sslRequired) {
            delegate.setSslRequired(sslRequired);
        }

        @Override
        public TokenStore getTokenStore() {
            return delegate.getTokenStore();
        }

        @Override
        public void setTokenStore(TokenStore tokenStore) {
            delegate.setTokenStore(tokenStore);
        }

        @Override
        public String getStateCookieName() {
            return delegate.getStateCookieName();
        }

        @Override
        public void setStateCookieName(String stateCookieName) {
            delegate.setStateCookieName(stateCookieName);
        }

        @Override
        public boolean isUseResourceRoleMappings() {
            return delegate.isUseResourceRoleMappings();
        }

        @Override
        public void setUseResourceRoleMappings(boolean useResourceRoleMappings) {
            delegate.setUseResourceRoleMappings(useResourceRoleMappings);
        }

        @Override
        public boolean isCors() {
            return delegate.isCors();
        }

        @Override
        public void setCors(boolean cors) {
            delegate.setCors(cors);
        }

        @Override
        public int getCorsMaxAge() {
            return delegate.getCorsMaxAge();
        }

        @Override
        public void setCorsMaxAge(int corsMaxAge) {
            delegate.setCorsMaxAge(corsMaxAge);
        }

        @Override
        public String getCorsAllowedHeaders() {
            return delegate.getCorsAllowedHeaders();
        }

        @Override
        public void setNotBefore(int notBefore) {
            delegate.setNotBefore(notBefore);
        }

        @Override
        public int getNotBefore() {
            return delegate.getNotBefore();
        }

        @Override
        public void setExposeToken(boolean exposeToken) {
            delegate.setExposeToken(exposeToken);
        }

        @Override
        public boolean isExposeToken() {
            return delegate.isExposeToken();
        }

        @Override
        public void setCorsAllowedMethods(String corsAllowedMethods) {
            delegate.setCorsAllowedMethods(corsAllowedMethods);
        }

        @Override
        public String getCorsAllowedMethods() {
            return delegate.getCorsAllowedMethods();
        }

        @Override
        public void setCorsAllowedHeaders(String corsAllowedHeaders) {
            delegate.setCorsAllowedHeaders(corsAllowedHeaders);
        }

        @Override
        public boolean isAlwaysRefreshToken() {
            return delegate.isAlwaysRefreshToken();
        }

        @Override
        public void setAlwaysRefreshToken(boolean alwaysRefreshToken) {
            delegate.setAlwaysRefreshToken(alwaysRefreshToken);
        }

        @Override
        public int getRegisterNodePeriod() {
            return delegate.getRegisterNodePeriod();
        }

        @Override
        public void setRegisterNodePeriod(int registerNodePeriod) {
            delegate.setRegisterNodePeriod(registerNodePeriod);
        }

        @Override
        public void setRegisterNodeAtStartup(boolean registerNodeAtStartup) {
            delegate.setRegisterNodeAtStartup(registerNodeAtStartup);
        }

        @Override
        public boolean isRegisterNodeAtStartup() {
            return delegate.isRegisterNodeAtStartup();
        }

        @Override
        public String getPrincipalAttribute() {
            return delegate.getPrincipalAttribute();
        }

        @Override
        public void setPrincipalAttribute(String principalAttribute) {
            delegate.setPrincipalAttribute(principalAttribute);
        }

        @Override
        public boolean isTurnOffChangeSessionIdOnLogin() {
            return delegate.isTurnOffChangeSessionIdOnLogin();
        }

        @Override
        public void setTurnOffChangeSessionIdOnLogin(boolean turnOffChangeSessionIdOnLogin) {
            delegate.setTurnOffChangeSessionIdOnLogin(turnOffChangeSessionIdOnLogin);
        }
    }

}
