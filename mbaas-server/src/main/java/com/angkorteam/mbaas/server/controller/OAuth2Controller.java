package com.angkorteam.mbaas.server.controller;

import com.angkorteam.mbaas.configuration.Constants;
import com.angkorteam.mbaas.model.entity.Tables;
import com.angkorteam.mbaas.model.entity.tables.ApplicationTable;
import com.angkorteam.mbaas.model.entity.tables.AuthorizationTable;
import com.angkorteam.mbaas.model.entity.tables.ClientTable;
import com.angkorteam.mbaas.model.entity.tables.MobileTable;
import com.angkorteam.mbaas.model.entity.tables.records.ApplicationRecord;
import com.angkorteam.mbaas.model.entity.tables.records.AuthorizationRecord;
import com.angkorteam.mbaas.model.entity.tables.records.ClientRecord;
import com.angkorteam.mbaas.model.entity.tables.records.MobileRecord;
import com.angkorteam.mbaas.plain.enums.GrantTypeEnum;
import com.angkorteam.mbaas.plain.response.oauth2.OAuth2AuthorizeResponse;
import com.angkorteam.mbaas.plain.response.oauth2.OAuth2ClientResponse;
import com.angkorteam.mbaas.plain.response.oauth2.OAuth2PasswordResponse;
import org.apache.commons.configuration.XMLPropertiesConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Created by socheat on 3/30/16.
 */
@Controller
@RequestMapping("/oauth2")
public class OAuth2Controller {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2Controller.class);

    @Autowired
    private DSLContext context;

    @RequestMapping(
            path = "/authorize",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<OAuth2AuthorizeResponse> authorize(
            HttpServletRequest request,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "code", required = false) String code) {
        LOGGER.info("{} client_id=>{} client_secret=>{} grant_type=>{} redirect_uri=>{} code=>{}", request.getRequestURL(), clientId, clientSecret, grantType, redirectUri, code);
        ClientTable clientTable = Tables.CLIENT.as("clientTable");
        ApplicationTable applicationTable = Tables.APPLICATION.as("applicationTable");
        MobileTable mobileTable = Tables.MOBILE.as("mobileTable");

        ClientRecord clientRecord = context.select(clientTable.fields()).from(clientTable).where(clientTable.CLIENT_ID.eq(clientId)).and(clientTable.CLIENT_SECRET.eq(clientSecret)).fetchOneInto(clientTable);
        if (clientRecord == null) {
            OAuth2AuthorizeResponse response = new OAuth2AuthorizeResponse();
            response.setHttpCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return ResponseEntity.badRequest().body(response);
        }
        ApplicationRecord applicationRecord = context.select(applicationTable.fields()).from(applicationTable).where(applicationTable.APPLICATION_ID.eq(clientRecord.getApplicationId())).fetchOneInto(applicationTable);
        if (applicationRecord == null) {
            OAuth2AuthorizeResponse response = new OAuth2AuthorizeResponse();
            response.setHttpCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return ResponseEntity.badRequest().body(response);
        }
        if (!"authorization_code".equals(grantType)) {
            OAuth2AuthorizeResponse response = new OAuth2AuthorizeResponse();
            response.setHttpCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return ResponseEntity.badRequest().body(response);
        }

        AuthorizationTable authorizationTable = Tables.AUTHORIZATION.as("authorizationTable");
        List<Condition> where = new ArrayList<>();
        where.add(authorizationTable.CLIENT_ID.eq(clientRecord.getClientId()));
        where.add(authorizationTable.APPLICATION_ID.eq(applicationRecord.getApplicationId()));
        where.add(authorizationTable.AUTHORIZATION_ID.eq(code));
        AuthorizationRecord authorizationRecord = context.select(authorizationTable.fields()).where(where).fetchOneInto(authorizationTable);
        if (authorizationRecord == null) {
            OAuth2AuthorizeResponse response = new OAuth2AuthorizeResponse();
            response.setHttpCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            return ResponseEntity.badRequest().body(response);
        }
        authorizationRecord.delete();

        XMLPropertiesConfiguration configuration = Constants.getXmlPropertiesConfiguration();

        MobileRecord mobileRecord = context.newRecord(mobileTable);
        mobileRecord.setMobileId(UUID.randomUUID().toString());
        mobileRecord.setApplicationId(applicationRecord.getApplicationId());
        mobileRecord.setClientId(clientRecord.getClientId());
        mobileRecord.setUserId(authorizationRecord.getUserId());
        mobileRecord.setClientIp(request.getRemoteAddr());
        mobileRecord.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
        mobileRecord.setDateCreated(new Date());
        mobileRecord.setTimeToLive(configuration.getInt(Constants.ACCESS_TOKEN_TIME_TO_LIVE));
        mobileRecord.setDateTokenIssued(new Date());
        mobileRecord.setAccessToken(UUID.randomUUID().toString());
        mobileRecord.setGrantType(GrantTypeEnum.Authorization.getLiteral());
        mobileRecord.store();

        OAuth2AuthorizeResponse response = new OAuth2AuthorizeResponse();
        response.setAccessToken(mobileRecord.getAccessToken());
        response.setRefreshToken(mobileRecord.getMobileId());
        response.setExpiresIn(mobileRecord.getTimeToLive());
        response.setTokenType("bearer");

        return ResponseEntity.ok(response);
    }

    @RequestMapping(
            path = "/implicit",
            method = {RequestMethod.POST, RequestMethod.GET}
    )
    public ResponseEntity<Void> implicit(
            HttpServletRequest request,
            @RequestParam(value = "response_type", required = false) String responseType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state
    ) {
        LOGGER.info("{} response_type=>{} client_id=>{} redirect_uri=>{} scope=>{} state=>{}", request.getRequestURL(), responseType, clientId, redirectUri, scope, state);

//        access_token 	Required. The access token assigned by the authorization server.
//        token_type 	Required. The type of the token
//        expires_in 	Recommended. A number of seconds after which the access token expires.
//        scope 	Optional. The scope of the access token.
//        state 	Required, if present in the autorization request. Must be same value as state parameter in request.
        List<String> params = new ArrayList<>();
        params.add("access_token=" + UUID.randomUUID().toString());
        params.add("token_type=bearer");
        params.add("expires_in=50000");
        params.add("scope=pp,ee,ww");
        params.add("state=wewewsdf21543");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, redirectUri + StringUtils.join(params, "&"));
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).headers(headers).build();
    }

    @RequestMapping(
            path = "/password",
            method = {RequestMethod.POST, RequestMethod.GET}
    )
    public ResponseEntity<OAuth2PasswordResponse> password(
            HttpServletRequest request,
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "scope", required = false) String scope
    ) {
        LOGGER.info("{} grant_type=>{} username=>{} password=>{} scope=>{}", request.getRequestURL(), grantType, username, password, scope);

        OAuth2PasswordResponse response = new OAuth2PasswordResponse();
        response.setTokenType("bearer");
        response.setAccessToken(UUID.randomUUID().toString());
        response.setExpiresIn(5000);
        response.setRefreshToken(UUID.randomUUID().toString());

        return ResponseEntity.ok(response);
    }

    @RequestMapping(
            path = "/client",
            method = {RequestMethod.POST, RequestMethod.GET}
    )
    public ResponseEntity<OAuth2ClientResponse> client(
            HttpServletRequest request,
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "scope", required = false) String scope
    ) {
        LOGGER.info("{} grant_type=>{} scope=>{}", request.getRequestURL(), grantType, scope);

        OAuth2ClientResponse response = new OAuth2ClientResponse();
        response.setTokenType("bearer");
        response.setAccessToken(UUID.randomUUID().toString());
        response.setExpiresIn(5000);

        return ResponseEntity.ok(response);
    }
}