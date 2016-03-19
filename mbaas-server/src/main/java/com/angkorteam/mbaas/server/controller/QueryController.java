package com.angkorteam.mbaas.server.controller;

import com.angkorteam.mbaas.configuration.Constants;
import com.angkorteam.mbaas.model.entity.Tables;
import com.angkorteam.mbaas.model.entity.tables.QueryParameterTable;
import com.angkorteam.mbaas.model.entity.tables.QueryTable;
import com.angkorteam.mbaas.model.entity.tables.records.QueryParameterRecord;
import com.angkorteam.mbaas.model.entity.tables.records.QueryRecord;
import com.angkorteam.mbaas.plain.enums.QueryInputParamTypeEnum;
import com.angkorteam.mbaas.plain.enums.QueryReturnTypeEnum;
import com.angkorteam.mbaas.plain.enums.SecurityEnum;
import com.angkorteam.mbaas.plain.request.query.QueryExecuteRequest;
import com.angkorteam.mbaas.plain.response.query.QueryExecuteResponse;
import com.google.gson.Gson;
import org.apache.commons.configuration.XMLPropertiesConfiguration;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by socheat on 2/22/16.
 */
@Controller
@RequestMapping(path = "/query")
public class QueryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryController.class);

    @Autowired
    private DSLContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Gson gson;

    @RequestMapping(
            path = "/execute/{query}",
            method = {RequestMethod.POST, RequestMethod.PUT},
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<QueryExecuteResponse> executeJson(
            HttpServletRequest request,
            @RequestHeader(name = "X-MBAAS-APPCODE", required = false) String appCode,
            @RequestHeader(name = "X-MBAAS-SESSION", required = false) String session,
            @PathVariable("query") String query,
            @RequestBody(required = false) QueryExecuteRequest requestBody
    ) {
        LOGGER.info("{} appCode=>{} session=>{} body=>{}", request.getRequestURL(), appCode, session, gson.toJson(requestBody));

        QueryTable queryTable = Tables.QUERY.as("queryTable");
        QueryRecord queryRecord = context.select(queryTable.fields()).from(queryTable).where(queryTable.PATH.eq(query)).fetchOneInto(queryTable);

        if (queryRecord == null || queryRecord.getScript() == null || "".equals(queryRecord.getScript()) || SecurityEnum.Denied.getLiteral().equals(queryRecord.getSecurity())) {
            QueryExecuteResponse response = new QueryExecuteResponse();
            response.setHttpCode(HttpStatus.METHOD_NOT_ALLOWED.value());
            return ResponseEntity.ok(response);
        }

        if (requestBody == null) {
            requestBody = new QueryExecuteRequest();
        }

        if (requestBody.getParameters() == null) {
            requestBody.setParameters(new LinkedHashMap<>());
        }

        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, String> errorMessages = queryValidation(query, requestBody, params);

        if (!errorMessages.isEmpty()) {
            QueryExecuteResponse response = new QueryExecuteResponse();
            response.setHttpCode(HttpStatus.BAD_REQUEST.value());
            response.getErrorMessages().putAll(errorMessages);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(query(query, params));
    }

    @RequestMapping(
            path = "/execute/{query}",
            method = RequestMethod.GET,
            consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<QueryExecuteResponse> execute(
            HttpServletRequest request,
            @RequestHeader(name = "X-MBAAS-APPCODE", required = false) String appCode,
            @RequestHeader(name = "X-MBAAS-SESSION", required = false) String session,
            @PathVariable("query") String query,
            @RequestBody(required = false) QueryExecuteRequest requestBody
    ) {
        LOGGER.info("{} appCode=>{} session=>{} body=>{}", request.getRequestURL(), appCode, session, gson.toJson(requestBody));

        QueryTable queryTable = Tables.QUERY.as("queryTable");
        QueryRecord queryRecord = context.select(queryTable.fields()).from(queryTable).where(queryTable.PATH.eq(query)).fetchOneInto(queryTable);

        if (queryRecord == null || queryRecord.getScript() == null || "".equals(queryRecord.getScript()) || SecurityEnum.Denied.getLiteral().equals(queryRecord.getSecurity())) {
            QueryExecuteResponse response = new QueryExecuteResponse();
            response.setHttpCode(HttpStatus.METHOD_NOT_ALLOWED.value());
            return ResponseEntity.ok(response);
        }

        QueryParameterTable queryParameterTable = Tables.QUERY_PARAMETER.as("queryParameterTable");
        
        Map<String, QueryParameterRecord> queryParameterRecords = new LinkedHashMap<>();
        for (QueryParameterRecord queryParameterRecord : context.select(queryParameterTable.fields()).from(queryParameterTable).where(queryParameterTable.QUERY_ID.eq(queryRecord.getQueryId())).fetchInto(queryParameterTable)) {
            queryParameterRecords.put(queryParameterRecord.getName(), queryParameterRecord);
        }

        if (requestBody == null) {
            requestBody = new QueryExecuteRequest();
            Enumeration<String> names = request.getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                String[] values = request.getParameterValues(name);
                if (values.length == 1) {
                    if (queryParameterRecords.containsKey(name)) {
                        QueryParameterRecord queryParameterRecord = queryParameterRecords.get(name);
                        Object value = parse(queryParameterRecord.getType(), values[0]);
                        if (value != null) {
                            requestBody.getParameters().put(name, value);
                        }
                    }
                } else {
                    requestBody.getParameters().put(name, Arrays.asList(values));
                }
            }
        }

        if (requestBody.getParameters() == null) {
            requestBody.setParameters(new LinkedHashMap<>());
            Enumeration<String> names = request.getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                String[] values = request.getParameterValues(name);
                if (values.length == 1) {
                    if (queryParameterRecords.containsKey(name)) {
                        QueryParameterRecord queryParameterRecord = queryParameterRecords.get(name);
                        Object value = parse(queryParameterRecord.getType(), values[0]);
                        if (value != null) {
                            requestBody.getParameters().put(name, value);
                        }
                    }
                } else {
                    requestBody.getParameters().put(name, Arrays.asList(values));
                }
            }
        }

        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, String> errorMessages = queryValidation(query, requestBody, params);

        if (!errorMessages.isEmpty()) {
            QueryExecuteResponse response = new QueryExecuteResponse();
            response.setHttpCode(HttpStatus.BAD_REQUEST.value());
            response.getErrorMessages().putAll(errorMessages);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.ok(query(query, params));
    }

    protected Object parse(String type, String value) {
        XMLPropertiesConfiguration configuration = Constants.getXmlPropertiesConfiguration();
        if (type.equals(QueryInputParamTypeEnum.Boolean.getLiteral())) {
            return Boolean.valueOf(value);
        } else if (type.equals(QueryInputParamTypeEnum.Byte.getLiteral())) {
            return Byte.valueOf(value);
        } else if (type.equals(QueryInputParamTypeEnum.Short.getLiteral())) {
            return Short.valueOf(value);
        } else if (type.equals(QueryInputParamTypeEnum.Integer.getLiteral())) {
            return Integer.valueOf(value);
        } else if (type.equals(QueryInputParamTypeEnum.Long.getLiteral())) {
            return Long.valueOf(value);
        } else if (type.equals(QueryInputParamTypeEnum.Float.getLiteral())) {
            return Float.valueOf(value);
        } else if (type.equals(QueryInputParamTypeEnum.Double.getLiteral())) {
            return Double.valueOf(value);
        } else if (type.equals(QueryInputParamTypeEnum.Character.getLiteral())) {
            if (value.length() == 1) {
                return value.charAt(0);
            }
        } else if (type.equals(QueryInputParamTypeEnum.String.getLiteral())) {
            return value;
        } else if (type.equals(QueryInputParamTypeEnum.Time.getLiteral())) {
            DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_TIME));
            try {
                return dateFormat.format(dateFormat.parse(value));
            } catch (ParseException e) {
                dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATETIME));
                try {
                    return dateFormat.format(dateFormat.parse(value));
                } catch (ParseException e1) {
                }
            }
        } else if (type.equals(QueryInputParamTypeEnum.Date.getLiteral())) {
            DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATE));
            try {
                return dateFormat.parse(value);
            } catch (ParseException e) {
                dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATETIME));
                try {
                    return dateFormat.format(dateFormat.parse(value));
                } catch (ParseException e1) {
                }
            }
        } else if (type.equals(QueryInputParamTypeEnum.DateTime.getLiteral())) {
            DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATETIME));
            try {
                return dateFormat.format(dateFormat.parse(value));
            } catch (ParseException e) {
            }
        }
        return null;
    }

    protected Map<String, String> queryValidation(String query, QueryExecuteRequest requestBody, Map<String, Object> params) {
        Map<String, String> errorMessages = new LinkedHashMap<>();

        QueryTable queryTable = Tables.QUERY.as("queryTable");
        QueryParameterTable queryParameterTable = Tables.QUERY_PARAMETER.as("queryParameterTable");

        XMLPropertiesConfiguration configuration = Constants.getXmlPropertiesConfiguration();

        QueryRecord queryRecord = context.select(queryTable.fields()).from(queryTable).where(queryTable.PATH.eq(query)).fetchOneInto(queryTable);
        List<QueryParameterRecord> queryParameterRecords = context.select(queryParameterTable.fields()).from(queryParameterTable).where(queryParameterTable.QUERY_ID.eq(queryRecord.getQueryId())).fetchInto(queryParameterTable);

        for (QueryParameterRecord queryParameterRecord : queryParameterRecords) {
            if (!requestBody.getParameters().containsKey(queryParameterRecord.getName())) {
                errorMessages.put(queryParameterRecord.getName(), "is required");
            } else {
                if (QueryInputParamTypeEnum.Boolean.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof Boolean) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not boolean");
                    }
                } else if (QueryInputParamTypeEnum.Byte.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof Byte) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not byte");
                    }
                } else if (QueryInputParamTypeEnum.Short.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof Short) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not short");
                    }
                } else if (QueryInputParamTypeEnum.Integer.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof Integer) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not integer");
                    }
                } else if (QueryInputParamTypeEnum.Long.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof Long) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not long");
                    }
                } else if (QueryInputParamTypeEnum.Float.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof Float) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not float");
                    }
                } else if (QueryInputParamTypeEnum.Double.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof Double) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not double");
                    }
                } else if (QueryInputParamTypeEnum.Character.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof Character) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not character");
                    }
                } else if (QueryInputParamTypeEnum.String.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof String) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not string");
                    }
                } else if (QueryInputParamTypeEnum.Time.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof String) {
                        DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_TIME));
                        Date value = null;
                        try {
                            value = dateFormat.parse((String) requestBody.getParameters().get(queryParameterRecord.getName()));
                            params.put(queryParameterRecord.getName(), value);
                        } catch (ParseException e) {
                            dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATETIME));
                            try {
                                value = dateFormat.parse((String) requestBody.getParameters().get(queryParameterRecord.getName()));
                                params.put(queryParameterRecord.getName(), value);
                            } catch (ParseException e1) {
                                errorMessages.put(queryParameterRecord.getName(), "is not time");
                            }
                        }
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not time");
                    }
                } else if (QueryInputParamTypeEnum.Date.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof String) {
                        DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATE));
                        Date value = null;
                        try {
                            value = dateFormat.parse((String) requestBody.getParameters().get(queryParameterRecord.getName()));
                            params.put(queryParameterRecord.getName(), value);
                        } catch (ParseException e) {
                            dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATETIME));
                            try {
                                value = dateFormat.parse((String) requestBody.getParameters().get(queryParameterRecord.getName()));
                                params.put(queryParameterRecord.getName(), value);
                            } catch (ParseException e1) {
                                errorMessages.put(queryParameterRecord.getName(), "is not date");
                            }
                        }
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not date");
                    }
                } else if (QueryInputParamTypeEnum.DateTime.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof String) {
                        DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATETIME));
                        Date value = null;
                        try {
                            value = dateFormat.parse((String) requestBody.getParameters().get(queryParameterRecord.getName()));
                            params.put(queryParameterRecord.getName(), value);
                        } catch (ParseException e) {
                            errorMessages.put(queryParameterRecord.getName(), "is not datetime");
                        }
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not datetime");
                    }
                } else if (QueryInputParamTypeEnum.List.getLiteral().equals(queryParameterRecord.getType())) {
                    if (requestBody.getParameters().get(queryParameterRecord.getName()) instanceof List) {
                        params.put(queryParameterRecord.getName(), requestBody.getParameters().get(queryParameterRecord.getName()));
                    } else {
                        errorMessages.put(queryParameterRecord.getName(), "is not list");
                    }
                }
            }
        }

        return errorMessages;
    }

    protected QueryExecuteResponse query(String query, Map<String, Object> params) {
        QueryTable queryTable = Tables.QUERY.as("queryTable");
        XMLPropertiesConfiguration configuration = Constants.getXmlPropertiesConfiguration();
        QueryRecord queryRecord = context.select(queryTable.fields()).from(queryTable).where(queryTable.PATH.eq(query)).fetchOneInto(queryTable);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        QueryExecuteResponse response = new QueryExecuteResponse();
        try {
            if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Boolean.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Boolean.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Byte.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Byte.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Short.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Short.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Integer.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Integer.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Long.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Long.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Float.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Float.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Double.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Double.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Character.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Character.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.String.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, String.class));
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Time.getLiteral())) {
                DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_TIME));
                Date value = namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Date.class);
                if (value != null) {
                    response.setData(dateFormat.format(value));
                }
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Date.getLiteral())) {
                DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATE));
                Date value = namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Date.class);
                if (value != null) {
                    response.setData(dateFormat.format(value));
                }
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.DateTime.getLiteral())) {
                DateFormat dateFormat = new SimpleDateFormat(configuration.getString(Constants.PATTERN_DATETIME));
                Date value = namedParameterJdbcTemplate.queryForObject(queryRecord.getScript(), params, Date.class);
                if (value != null) {
                    response.setData(dateFormat.format(value));
                }
            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.Map.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForMap(queryRecord.getScript(), params));

            } else if (queryRecord.getReturnType().equals(QueryReturnTypeEnum.List.getLiteral())) {
                response.setData(namedParameterJdbcTemplate.queryForList(queryRecord.getScript(), params));
            }
        } catch (EmptyResultDataAccessException e) {
        } catch (IncorrectResultSetColumnCountException | IncorrectResultSizeDataAccessException | BadSqlGrammarException e) {
            response.setHttpCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setResult(e.getMessage());
        }
        return response;
    }
}
