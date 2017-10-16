package cn.edu.pku.sei.SnowView.servlet;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Created by Administrator on 2017/5/26.
 */
public class RandomServlet extends HttpServlet {

	Random rand ;
	Map<Integer, Pair<Integer,Integer>> map = new HashMap<Integer, Pair<Integer,Integer>>();
	
	public void init(ServletConfig config) throws ServletException{
		
		if (Config.getComputerUrl()!=null)
			return;
		
		rand = new Random();

        /* 读取数据 */
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(Config.getExampleFilePath())),
                                                                         "UTF-8"));
            String lineTxt = null;
            while ((lineTxt = br.readLine()) != null) {
                String[] names = lineTxt.split(" ");
                map.put(Integer.parseInt(names[0]), new ImmutablePair<Integer,Integer>(Integer.parseInt(names[1]), Integer.parseInt(names[2])));
            }
            br.close();
        } catch (Exception e) {
            System.err.println("read errors :" + e);
        }		
	}
	
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	
    	if (Config.sendToSlaveUrl(request,response,"Random")==1)
    		return;

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        long id = rand.nextInt(map.size());
        String query = Config.getDocSearcher().getContent(map.get((int)id).getLeft()).getLeft();
        String query2 = Config.getDocSearcher().getContent(map.get((int)id).getLeft()).getRight();
        JSONObject searchResult = new JSONObject();

        searchResult.put("query", query);
        searchResult.put("query2", query2);
        searchResult.put("answerId", map.get((int)id).getRight());

        response.getWriter().print(searchResult.toString());
    }

}