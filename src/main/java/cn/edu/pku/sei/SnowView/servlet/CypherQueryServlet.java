package cn.edu.pku.sei.SnowView.servlet;
import graphsearcher.SearchResult;

import org.json.JSONObject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Created by Administrator on 2017/5/26.
 */
public class CypherQueryServlet extends HttpServlet {

    List <SearchResult> resultCache;
    int resultLength;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    	if (Config.sendToSlaveUrl(request,response,"CypherQuery")==1)
    		return;
    	
        request.setCharacterEncoding("UTF-8");
        String queryText = request.getParameter("query");

        JSONObject searchResult = new JSONObject();

        List<SearchResult> results = Config.getGraphSearcher().query(queryText);
        //System.out.println(results.size());
        if(results == null || results.size() == 0){
            searchResult = null;
        }else{
            searchResult = results.get(0).toJSON(Config.getGraphDB());
        }
        // 注释代码为返回结果为多个的时候，上下浏览的功能
        //null是初始搜索结果 getGraph 是浏览已存储的结果
        /*int index = 0;
        if(requestType == null){
        	resultCache = searcher.query(queryText);
        	searchResult = resultCache.get(0).toJSON(db);
	        resultLength = resultCache.size();
	        index = 0;
	    }else if(requestType.compareTo("getGraph") == 0){
	    	index = Integer.parseInt(request.getParameter("index"));
	    	SearchResult results = resultCache.get(index);
	    	searchResult = results.toJSON(db);
	    }*/

        JSONObject result = new JSONObject();
        result.put("searchResult" , searchResult);
        //result.put("index", index);  //注释原因同上
        //result.put("max" , resultLength);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().print(result.toString());

    }
}