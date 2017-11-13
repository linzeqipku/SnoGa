package graphdb.extractors.linkers.codeindoc;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import graphdb.extractors.miners.text.TextExtractor;
import graphdb.extractors.parsers.javacode.JavaCodeExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import graphdb.framework.Extractor;
import graphdb.framework.annotations.RelationshipDeclaration;

public class CodeInDocExtractor implements Extractor {

    @RelationshipDeclaration
    public static final String DOC_LEVEL_REFER = "docRef";
    @RelationshipDeclaration
    public static final String LEX_LEVEL_REFER = "lexRef";

    GraphDatabaseService db = null;
    CodeIndexes codeIndexes = null;

    Map<Node, String> nodeToTextMap = new HashMap<Node, String>();

    public void run(GraphDatabaseService db) {
        this.db = db;
        codeIndexes = new CodeIndexes(db);
        try (Transaction tx=db.beginTx()){
        	for (Node node:db.getAllNodes()){
        		if (!node.hasProperty(TextExtractor.TEXT))
        			continue;
        		if (node.hasLabel(Label.label(JavaCodeExtractor.CLASS)))
        			continue;
        		if (node.hasLabel(Label.label(JavaCodeExtractor.METHOD)))
        			continue;
        		if (node.hasLabel(Label.label(JavaCodeExtractor.INTERFACE)))
        			continue;
        		if (node.hasLabel(Label.label(JavaCodeExtractor.FIELD)))
        			continue;
        		String text="<html>"+node.getProperty(TextExtractor.TEXT)+"</html>";
        		nodeToTextMap.put(node, text);
        	}
        	tx.success();
        }
        findDocLevelReference();
        findLexLevelReference();
    }

    void findDocLevelReference() {
        try (Transaction tx = db.beginTx()) {
            for (Node srcNode : nodeToTextMap.keySet()) {
                String content = nodeToTextMap.get(srcNode);
                Set<String> tokens = new HashSet<String>();
                for (String token : content.split("\\W+"))
                    if (token.length() > 0)
                        tokens.add(token);

                //找到所有名字在文本中出现过的类
                Map<String, Set<Long>> occTypeMap = new HashMap<String, Set<Long>>();
                for (String typeShortName : codeIndexes.typeShortNameMap.keySet())
                    if (tokens.contains(typeShortName))
                        occTypeMap.put(typeShortName, codeIndexes.typeShortNameMap.get(typeShortName));
                //找到所有名字在文本中出现过的方法
                Map<String, Set<Long>> occMethodMap = new HashMap<String, Set<Long>>();
                for (String methodShortName : codeIndexes.methodShortNameMap.keySet())
                    if (tokens.contains(methodShortName))
                        occMethodMap.put(methodShortName, codeIndexes.methodShortNameMap.get(methodShortName));

                //链接的类和方法
                Set<Long> linkTypeSet = new HashSet<Long>();
                Set<Long> linkMethodSet = new HashSet<Long>();
                //所有的链接地址
                String ahrefs = "";
                Elements links = Jsoup.parse(content).select("a[href]");
                for (Element link : links)
                    ahrefs += link.attr("href") + "||||||||||";
                try {
                    ahrefs = URLDecoder.decode(ahrefs.replaceAll("%(?![0-9a-fA-F]{2})", "%25").replaceAll("\\+", "%2B"), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                //精确链接匹配到的方法
                for (String methodShortName : occMethodMap.keySet())
                    for (long id : occMethodMap.get(methodShortName)) {
                        String str = ((String) db.getNodeById(id).getProperty(JavaCodeExtractor.METHOD_BELONGTO)).replace(".", "/");
                        str += ".html#" + methodShortName + "(" + db.getNodeById(id).getProperty(JavaCodeExtractor.METHOD_PARAMS) + ")";
                        if (ahrefs.contains(str))
                            linkMethodSet.add(id);
                    }
                //精确链接匹配到的类
                for (String typeShortName : occTypeMap.keySet())
                    for (long id : occTypeMap.get(typeShortName)) {
                        String str = ((String) db.getNodeById(id).getProperty(JavaCodeExtractor.CLASS_FULLNAME)).replace(".", "/");
                        str += ".html|";
                        if (ahrefs.contains(str))
                            linkTypeSet.add(id);
                    }
                //建立链接关联
                for (long id : linkMethodSet)
                    srcNode.createRelationshipTo(db.getNodeById(id), RelationshipType.withName(DOC_LEVEL_REFER));
                for (long id : linkTypeSet)
                    srcNode.createRelationshipTo(db.getNodeById(id), RelationshipType.withName(DOC_LEVEL_REFER));

            }
            tx.success();
        }
    }

    /**
     * Lex-level reference:
     * 对于类/接口：名字在文中出现；
     * 对于方法：后接小括号，重名不多或所属的类/接口已出现过.
     */
    void findLexLevelReference() {
        try (Transaction tx = db.beginTx()) {

            for (Node srcNode : nodeToTextMap.keySet()) {
                String content = Jsoup.parse(nodeToTextMap.get(srcNode)).text();
                Set<String> lexes = new HashSet<String>();
                Collections.addAll(lexes, content.split("\\W+"));
                Set<Node> resultNodes = new HashSet<Node>();

                //类/接口
                for (String typeShortName : codeIndexes.typeShortNameMap.keySet())
                    if (lexes.contains(typeShortName))
                        for (long id : codeIndexes.typeShortNameMap.get(typeShortName))
                            resultNodes.add(db.getNodeById(id));

                for (String methodShortName : codeIndexes.methodShortNameMap.keySet()) {
                    //后接小括号，不要构造函数
                    if (methodShortName.charAt(0) < 'a' || methodShortName.charAt(0) > 'z' || !(lexes.contains(methodShortName) && content.contains(methodShortName + "(")))
                        continue;
                    boolean flag = false;
                    //无歧义
                    if (codeIndexes.methodShortNameMap.get(methodShortName).size() == 1) {
                        for (long id : codeIndexes.methodShortNameMap.get(methodShortName))
                            resultNodes.add(db.getNodeById(id));
                        flag = true;
                    }
                    //主类在
                    for (long methodNodeId : codeIndexes.methodShortNameMap.get(methodShortName)) {
                        Node methodNode = db.getNodeById(methodNodeId);
                        if (resultNodes.contains(methodNode.getRelationships(RelationshipType.withName(JavaCodeExtractor.HAVE_METHOD), Direction.INCOMING).iterator().next().getStartNode())) {
                            resultNodes.add(methodNode);
                            flag = true;
                        }
                    }
                    //歧义不多
                    if (!flag && codeIndexes.methodShortNameMap.get(methodShortName).size() <= 5)
                        for (long id : codeIndexes.methodShortNameMap.get(methodShortName))
                            resultNodes.add(db.getNodeById(id));
                }

                for (Node rNode : resultNodes)
                    srcNode.createRelationshipTo(rNode, RelationshipType.withName(LEX_LEVEL_REFER));

            }

            tx.success();
        }
    }

}
