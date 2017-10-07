package solr;

import graphsearcher.GraphSearcher;
import graphsearcher.SearchResult;
import javafx.util.Pair;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.jsoup.Jsoup;
import org.neo4j.cypher.internal.frontend.v2_3.perty.Doc;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by laurence on 17-9-28.
 */
public class SolrKeeper {
    SolrClient client = null;

    public SolrKeeper(String baseUrl){
        client = new HttpSolrClient.Builder(baseUrl).build();
    }

    public void addGraphToIndex(String path, String coreName){
        GraphDatabaseFactory graphDbFactory = new GraphDatabaseFactory();
        GraphDatabaseService graphDb = graphDbFactory.newEmbeddedDatabase(new File(path));

        DocumentExtractor documentExtractor = new DocumentExtractor(graphDb);
        GraphSearcher graphSearcher = new GraphSearcher(graphDb);

        List<SolrInputDocument> documentList = new ArrayList<>();
        System.out.println("doc list size: " + documentExtractor.docIdList.size());
        for(long id : documentExtractor.docIdList){
            System.out.println("current id: " + id);
            String org_content = documentExtractor.getOrgText(graphDb, id);
            String content = Jsoup.parse("<html>" + org_content + "</html>").text();
            List<SearchResult> queryList = graphSearcher.query(content);
            SearchResult subGraph = queryList.size() > 0 ? queryList.get(0) : new SearchResult();
            String nodeSet = "";
            for (long nodeId : subGraph.nodes){
                nodeSet += nodeId + " ";
            }
            nodeSet = nodeSet.trim();
            if (content.length() > 0) {
                SolrInputDocument document = new SolrInputDocument();
                document.addField("id", id);
                document.addField("content", content);
                document.addField("org_content", org_content);
                document.addField("node_set", nodeSet);
                documentList.add(document);
            }
            if (documentList.size() >= 600) {
                try {
                    client.add(coreName, documentList);
                    System.out.println("add doc to server");
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SolrServerException e) {
                    e.printStackTrace();
                }
                documentList.clear();
            }
        }
        try{
            client.add(coreName, documentList);
        } catch (IOException e){
            e.printStackTrace();
        } catch (SolrServerException e){
            e.printStackTrace();
        }
        graphDb.shutdown();
    }

    public List<Pair<Long, Set<Long>>> querySolr(String q, String coreName){
        SolrQuery solrQuery = new SolrQuery(q);
        solrQuery.setRows(100);
        List<Pair<Long, Set<Long>>> resPairList = new ArrayList<>();
        try {
            QueryResponse response = client.query(coreName, solrQuery);
            SolrDocumentList docs = response.getResults();
            for (SolrDocument doc : docs){
                Long id = Long.parseLong((String)doc.getFieldValue("id"));
                String subGraph = (String)doc.getFieldValue("node_set");
                Set<Long> nodeSet = new HashSet<Long>();
                for (String node : subGraph.trim().split(" ")){
                    nodeSet.add(Long.parseLong(node));
                }
                resPairList.add(new Pair<>(id, nodeSet));
            }
        } catch (SolrServerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return resPairList;
    }

    public static void main(String args[]){
        SolrKeeper keeper = new SolrKeeper("http://localhost:8983/solr");
        keeper.addGraphToIndex("E:\\Ling\\graphdb-lucene-embedding", "myCore");
    }
}
