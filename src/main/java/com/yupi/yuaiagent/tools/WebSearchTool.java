package com.yupi.yuaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            return parseSearchResults(response);
        } catch (Exception e) {
            return "Error searching Baidu: " + e.getMessage();
        }
    }

    static String parseSearchResults(String response) {
        JSONObject jsonObject = JSONUtil.parseObj(response);

        Object error = jsonObject.get("error");
        if (error != null && !(error instanceof JSONNull)) {
            return "Search API error: " + error;
        }

        JSONArray organicResults = jsonObject.getJSONArray("organic_results");
        if (organicResults == null || organicResults.isEmpty()) {
            organicResults = jsonObject.getJSONArray("organicResults");
        }
        if (organicResults == null || organicResults.isEmpty()) {
            return "No search results found.";
        }

        List<Object> topResults = new ArrayList<>(organicResults.subList(0, Math.min(5, organicResults.size())));
        return topResults.stream()
                .map(obj -> (JSONObject) obj)
                .map(WebSearchTool::formatResult)
                .collect(Collectors.joining("\n"));
    }

    private static String formatResult(JSONObject result) {
        String title = result.getStr("title", "No title");
        String link = result.getStr("link", result.getStr("url", "No url"));
        String snippet = result.getStr("snippet", result.getStr("description", ""));
        return String.format("title: %s\nlink: %s\nsnippet: %s", title, link, snippet);
    }
}
