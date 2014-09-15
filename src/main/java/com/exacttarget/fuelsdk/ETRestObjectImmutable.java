//
// This file is part of the Fuel Java SDK.
//
// Copyright (C) 2013, 2014 ExactTarget, Inc.
// All rights reserved.
//
// Permission is hereby granted, free of charge, to any person
// obtaining a copy of this software and associated documentation
// files (the "Software"), to deal in the Software without restriction,
// including without limitation the rights to use, copy, modify,
// merge, publish, distribute, sublicense, and/or sell copies
// of the Software, and to permit persons to whom the Software
// is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
// KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
// WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
// PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
// OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
// OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
// OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
// THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//

package com.exacttarget.fuelsdk;

import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.log4j.Logger;

import com.exacttarget.fuelsdk.annotations.RestAnnotations;

public abstract class ETRestObjectImmutable extends ETObject {
    private static Logger logger = Logger.getLogger(ETRestObjectImmutable.class);

    private String id = null;
    private String key = null;
    private Date createdDate = null;
    private Date modifiedDate = null;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    protected void toStringOpen() {
        toStringReset();
        toStringAppend(getClass().getName());
        toStringAppend("[");
        toStringAppend("id", getId());
        toStringAppend("key", getKey());
        toStringAppend("createdDate", getCreatedDate());
        toStringAppend("modifiedDate", getModifiedDate());
    }

    protected void toStringClose() {
        toStringAppend("]", false); // don't want a new line here
    }

    public static <T extends ETRestObjectImmutable> ETResponse<T> retrieve(ETClient client,
                                                                  Class<T> type)
        throws ETSdkException
    {
        return retrieve(client, null, null, null, type);
    }

    public static <T extends ETRestObjectImmutable> ETResponse<T> retrieve(ETClient client,
                                                                  Integer page,
                                                                  Integer pageSize,
                                                                  Class<T> type)
        throws ETSdkException
    {
        return retrieve(client, null, page, pageSize, type);
    }

    public static <T extends ETRestObjectImmutable> ETResponse<T> retrieve(ETClient client,
                                                                  String filter,
                                                                  Class<T> type)
        throws ETSdkException
    {
        return retrieve(client, filter, null, null, type);
    }

    public static <T extends ETRestObjectImmutable> ETResponse<T> retrieve(ETClient client,
                                                                  String filter,
                                                                  Integer page,
                                                                  Integer pageSize,
                                                                  Class<T> type)
        throws ETSdkException
    {
        ETResponse<T> response = new ETResponse<T>();

        ETRestConnection connection = client.getRestConnection();

        Gson gson = connection.getGson();

        JsonParser jsonParser = new JsonParser();

        RestAnnotations annotations = type.getAnnotation(RestAnnotations.class);

        assert annotations != null;

        String path = annotations.path();
        logger.trace("path: " + path);
        String primaryKey = annotations.primaryKey();
        logger.trace("primaryKey: " + primaryKey);
        String collectionKey = annotations.collectionKey();
        logger.trace("collectionKey: " + collectionKey);

        if (filter != null) {
            //
            // Replace all variables in the path per the filter:
            //

            logger.trace("filter: " + filter);

            // XXX should throw an exception for complex expressions

            ETFilterExpression parsedFilter = new ETFilterExpression(filter);

            // XXX doesn't support multiple variables yet

            path = replaceVariable(path,
                                   parsedFilter.getColumn(),
                                   parsedFilter.getValues().get(0));

            // XXX should throw an exception if not all are specified
        } else {
            //
            // Remove the primary key from the end of the path:
            //

            path = removePrimaryKeyFromEnd(path, primaryKey);
        }

        StringBuilder stringBuilder = new StringBuilder(path);

        if (page != null && pageSize != null) {
            stringBuilder.append("?");
            stringBuilder.append("$page=");
            stringBuilder.append(page);
            stringBuilder.append("&");
            stringBuilder.append("$pagesize=");
            stringBuilder.append(pageSize);
        }

        path = stringBuilder.toString();

        logger.trace("GET " + path);

        String json = connection.get(path);

        JsonObject jsonObject = jsonParser.parse(json).getAsJsonObject();

        if (logger.isTraceEnabled()) {
            String jsonPrettyPrinted = gson.toJson(jsonObject);
            for (String line : jsonPrettyPrinted.split("\\n")) {
                logger.trace(line);
            }
        }

        if (jsonObject.get("page") != null) {
            response.setPage(jsonObject.get("page").getAsInt());
            logger.trace("page = " + response.getPage());
            response.setPageSize(jsonObject.get("pageSize").getAsInt());
            logger.trace("pageSize = " + response.getPageSize());
            JsonElement totalCount = jsonObject.get("totalCount");
            if (totalCount == null) {
                // XXX this should be standardized
                totalCount = jsonObject.get("count");
            }
            response.setTotalCount(totalCount.getAsInt());
            logger.trace("totalCount = " + response.getTotalCount());

            if (response.getPage() * response.getPageSize() < response.getTotalCount()) {
                response.setMoreResults(true);
            }

            JsonArray collection = jsonObject.get(collectionKey).getAsJsonArray();

            for (JsonElement element : collection) {
                response.addResult(gson.fromJson(element, type));
            }
        } else {
            response.addResult(gson.fromJson(json, type));
        }

        // XXX set requestId, statusCode, and statusMessage

        return response;
    }

    protected static String removePrimaryKeyFromEnd(String path, String primaryKey)
        throws ETSdkException
    {
        StringBuilder stringBuilder = new StringBuilder(path);
        int index = stringBuilder.lastIndexOf("/");
        if (!stringBuilder.substring(index + 1).equals("{" + primaryKey + "}")) {
            throw new ETSdkException("path \""
                                     + path
                                     + "\" does not end with variable \"{"
                                     + primaryKey
                                     + "}\"");
        }
        stringBuilder.delete(index, stringBuilder.length());
        return stringBuilder.toString();
    }

    protected static String replaceVariable(String path,
                                            String key,
                                            String value)
        throws ETSdkException
    {
        StringBuilder stringBuilder = new StringBuilder(path);
        String variable = "{" + key + "}";
        int index = stringBuilder.indexOf(variable);
        if (index == -1) {
            throw new ETSdkException("path \""
                                     + path
                                     + "\" does not contain variable \"{"
                                     + key
                                     + "}\"");
        }
        stringBuilder.replace(index, index + variable.length(), value);
        return stringBuilder.toString();
    }
}