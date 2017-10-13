package graphsearcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpanders;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterable;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.tartarus.snowball.ext.EnglishStemmer;

import cn.edu.pku.sei.SnowView.servlet.Config;
import graphdb.extractors.linkers.codeindoc_ch.CodeInDocxFileExtractor;
import graphdb.extractors.linkers.designtorequire_ch.DesignToRequireExtractor;
import graphdb.extractors.miners.codeembedding.line.LINEExtracter;
import graphdb.extractors.miners.tokenization_ch.TokenChExtractor;
import graphdb.extractors.parsers.javacode.JavaCodeExtractor;

public class GraphSearcher {

	GraphDatabaseService db = null;
	PathFinder<Path> pathFinder = GraphAlgoFactory
			.shortestPath(PathExpanders.forTypesAndDirections(RelationshipType.withName(JavaCodeExtractor.EXTEND),
					Direction.BOTH, RelationshipType.withName(JavaCodeExtractor.IMPLEMENT), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.THROW), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.PARAM), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.RT), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.HAVE_METHOD), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.HAVE_FIELD), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.CALL_METHOD), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.CALL_FIELD), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.TYPE), Direction.BOTH,
					RelationshipType.withName(JavaCodeExtractor.VARIABLE), Direction.BOTH), 5);

	boolean debug = false;

	static EnglishStemmer stemmer = new EnglishStemmer();
	static QueryStringToQueryWordsConverter converter = new QueryStringToQueryWordsConverter();

	public Map<Long, List<Double>> id2Vec = new HashMap<>();
	Map<Long, String> id2Sig = new HashMap<>();
	Map<Long, String> id2Name = new HashMap<>();
	Map<Long, Set<String>> id2Words = new HashMap<>();
	Set<Long> typeSet = new HashSet<>();

	Map<String, Set<Long>> word2Ids = new HashMap<>();

	Map<String, Set<Long>> queryWord2Ids = new HashMap<>();
	Set<String> queryWordSet = new HashSet<>();

	public static void main(String[] args){
		GraphDatabaseService db=Config.getGraphDB();
		GraphSearcher searcher=new GraphSearcher(db);
		System.out.println(searcher.querySingle("IndexWriter").nodes);
	}

	public GraphSearcher(GraphDatabaseService db) {
		this.db = db;
		try (Transaction tx = db.beginTx()) {
			ResourceIterable<Node> nodes = db.getAllNodes();
			for (Node node : nodes) {
				if (!node.hasLabel(Label.label(JavaCodeExtractor.CLASS))
						&& !node.hasLabel(Label.label(JavaCodeExtractor.INTERFACE))
						&& !node.hasLabel(Label.label(JavaCodeExtractor.METHOD)))
					continue;
				if (!node.hasProperty(LINEExtracter.LINE_VEC))
					continue;
				String[] eles = ((String) node.getProperty(LINEExtracter.LINE_VEC)).trim().split("\\s+");
				List<Double> vec = new ArrayList<Double>();
				for (String e : eles)
					vec.add(Double.parseDouble(e));
				long id = node.getId();
				String sig = (String) node.getProperty(JavaCodeExtractor.SIGNATURE);
				if (sig.toLowerCase().contains("test"))
					continue;
				String name = "";
				if (node.hasLabel(Label.label(JavaCodeExtractor.CLASS)))
					name = (String) node.getProperty(JavaCodeExtractor.CLASS_NAME);
				if (node.hasLabel(Label.label(JavaCodeExtractor.INTERFACE)))
					name = (String) node.getProperty(JavaCodeExtractor.INTERFACE_NAME);
				if (node.hasLabel(Label.label(JavaCodeExtractor.METHOD)))
					name = (String) node.getProperty(JavaCodeExtractor.METHOD_NAME);
				if (node.hasLabel(Label.label(JavaCodeExtractor.METHOD)) && name.matches("[A-Z]\\w+"))
					continue;
				Set<String> words = new HashSet<>();
				for (String e : name.split("[^A-Za-z]+"))
					for (String word : camelSplit(e)) {
						word = stem(word);
						if (!word2Ids.containsKey(word))
							word2Ids.put(word, new HashSet<>());
						word2Ids.get(word).add(id);
						words.add(word);
					}
				if (node.hasProperty(TokenChExtractor.TOKENS_CH)) {
					String cTokenString = (String) node.getProperty(TokenChExtractor.TOKENS_CH);
					for (String word : cTokenString.trim().split("\\s+")) {
						if (!word2Ids.containsKey(word))
							word2Ids.put(word, new HashSet<>());
						word2Ids.get(word).add(id);
						words.add(word);
					}
				}
				id2Words.put(id, words);
				id2Vec.put(id, vec);
				id2Sig.put(id, sig);
				if (node.hasLabel(Label.label(JavaCodeExtractor.CLASS))
						|| node.hasLabel(Label.label(JavaCodeExtractor.INTERFACE)))
					typeSet.add(id);
				id2Name.put(id, stem(name.toLowerCase()));
			}
			System.out.println(id2Name.values());
			tx.success();
		}
	}

	public SearchResult querySingle(String queryString) {
		List<SearchResult> list = query(queryString);
		return list.size() > 0 ? list.get(0) : new SearchResult();
	}

	public List<SearchResult> query(String queryString) {

		List<SearchResult> r = new ArrayList<>();

		/*
		 * seedMap: - key: 定位到的代码元素结点的集合 - value: 这个集合的离散度，离散度越低，说明这个图的质量越好
		 */
		Map<Set<Long>, Double> seedMap = findSubGraphs(queryString);

		/*
		 * 将定位到的代码元素连起来，形成一个子图，从而使得用户可以看出它们之间的关联关系
		 */
		try (Transaction tx = db.beginTx()) {
			for (Set<Long> seeds : seedMap.keySet()) {
				SearchResult searchResult = new SearchResult();
				searchResult.nodes.addAll(seeds);
				
				Set<Long> flags=new HashSet<>();
				for (long seed1 : seeds){
					if (flags.contains(seed1))
						continue;
					for (long seed2 : seeds) {
						if (seed1==seed2)
							continue;
						if (flags.contains(seed2))
							continue;
						Path path = pathFinder.findSinglePath(db.getNodeById(seed1), db.getNodeById(seed2));
						if (path != null) {
							for (Node node : path.nodes())
								searchResult.nodes.add(node.getId());
							for (Relationship edge : path.relationships())
								searchResult.edges.add(edge.getId());
							flags.add(seed2);
						}
					}
					flags.add(seed1);
				}
				Set<Long> tmpSet = new HashSet<>();
				tmpSet.addAll(searchResult.nodes);
				for (long node : tmpSet) {
					Iterator<Relationship> classInter = db.getNodeById(node).getRelationships(
							RelationshipType.withName(JavaCodeExtractor.HAVE_METHOD), Direction.INCOMING).iterator();
					if (!classInter.hasNext())
						continue;
					Relationship edge = classInter.next();
					searchResult.nodes.add(edge.getStartNodeId());
					searchResult.edges.add(edge.getId());
				}
				tmpSet.clear();
				tmpSet.addAll(searchResult.nodes);
				for (long node : tmpSet) {
					Iterator<Relationship> iter = db.getNodeById(node)
							.getRelationships(RelationshipType.withName(CodeInDocxFileExtractor.API_EXPLAINED_BY),
									Direction.BOTH)
							.iterator();
					while (iter.hasNext()) {
						Relationship edge = iter.next();
						searchResult.nodes.add(edge.getOtherNodeId(node));
						searchResult.edges.add(edge.getId());
					}
				}
				tmpSet.clear();
				tmpSet.addAll(searchResult.nodes);
				for (long node : tmpSet) {
					Iterator<Relationship> iter = db.getNodeById(node).getRelationships(
							RelationshipType.withName(DesignToRequireExtractor.DESIGNED_BY), Direction.BOTH).iterator();
					while (iter.hasNext()) {
						Relationship edge = iter.next();
						searchResult.nodes.add(edge.getOtherNodeId(node));
						searchResult.edges.add(edge.getId());
					}
				}
				searchResult.cost = seedMap.get(seeds);
				r.add(searchResult);
			}
			tx.success();
		}

		/*
		 * 排序并取前5个子图返回
		 */
		Collections.sort(r, (r1, r2) -> Double.compare(r1.cost, r2.cost));
		int K = 5;
		if (r.size() < K)
			return r;
		else
			return r.subList(0, K);
	}

	/**
	 * 
	 * @param queryString
	 *            用户输入的自然语言查询语句(英文)
	 * @return 定位到各个代码元素集合，以及这些集合的离散度
	 */
	public Map<Set<Long>, Double> findSubGraphs(String queryString) {

		Map<Set<Long>, Double> r = new HashMap<>();

		queryWord2Ids = new HashMap<>();
		queryWordSet = new HashSet<>();

		queryWordSet = converter.convert(queryString);

		if (debug) {
			System.out.println("queryWordSet = { " + String.join(", ", queryWordSet) + " }");
			System.out.println();
		}

		/*
		 * 找到能够和各个查询单词匹配得上的代码元素 queryWordSet: 有代码元素能够匹配得上的查询单词 queryWord2Ids:
		 * 这些单词到相应的代码元素的映射
		 */
		queryWord2Ids(queryWordSet);

		Set<Long> anchors = findAnchors();
		//System.out.println(anchors);

		if (anchors.size() > 0) {
			Set<Long> subGraph = new HashSet<>();
			subGraph.addAll(anchors);
			for (String queryWord : queryWordSet) {
				Set<Long> set = new HashSet<>();
				set.addAll(queryWord2Ids.get(queryWord));
				set.retainAll(anchors);
				if (set.size() > 0)
					continue;
				double minDist = Double.MAX_VALUE;
				long minDistNode = -1;
				for (long node : queryWord2Ids.get(queryWord)) {
					for (long anchor : anchors) {
						double dist = dist(anchor, node);
						if (dist < minDist) {
							minDist = dist;
							minDistNode = node;
						}
					}
				}
				if (minDistNode == -1)
					continue;
				subGraph.add(minDistNode);
			}
			r.put(subGraph, 1.0);
			return r;
		}

		Set<Long> candidateNodes = candidate();

		/*
		 * 对于每一个候选的起点， 从每个查询单词对应的代码元素集合中选取一个离它最近的代码元素
		 */
		for (long cNode : candidateNodes) {
			Set<Long> minDistNodeSet = new HashSet<>();
			minDistNodeSet.add(cNode);
			for (String queryWord : queryWordSet) {
				if (queryWord2Ids.get(queryWord).contains(cNode))
					continue;
				double minDist = Double.MAX_VALUE;
				long minDistNode = -1;
				for (long node : queryWord2Ids.get(queryWord)) {
					double dist = dist(cNode, node);
					if (dist < minDist) {
						minDist = dist;
						minDistNode = node;
					}
				}
				if (minDistNode == -1)
					continue;
				minDistNodeSet.add(minDistNode);
			}
			double cost = sumDist(cNode, minDistNodeSet);
			r.put(minDistNodeSet, cost);
		}

		return r;

	}

	/**
	 * 判断queryWordSet中的哪些单词可以作为搜索的起点
	 * 
	 * @return
	 */
	Set<Long> candidate() {

		Set<Long> candidateNodes = new HashSet<>();

		for (long node : typeSet)
			if (queryWordSet.contains(id2Name.get(node)))
				candidateNodes.add(node);

		/**
		 * countSet: {<key=代码元素，value=这个代码元素对应到了几个查询单词>|value>=2}
		 */
		Set<Pair<Long, Integer>> countSet = new HashSet<>();
		int maxCount = 0;
		for (long node : id2Name.keySet()) {
			int count = 0;
			for (String word : id2Words.get(node))
				if (queryWordSet.contains(word))
					count++;

			if (count >= 2) {
				countSet.add(new ImmutablePair<Long, Integer>(node, count));
				if (count > maxCount)
					maxCount = count;
			}
		}

		/**
		 * 如果一个代码元素对应到了多个查询单词，且是最多的，则它可以作为搜索的起点
		 * 例如：代码元素SoftKittyWarmKittyLittleBallOfFur, 查询单词soft,kitty, warm,
		 * little, fur
		 */
		for (Pair<Long, Integer> pair : countSet) {
			if (pair.getValue() == maxCount)
				candidateNodes.add(pair.getKey());
		}

		/**
		 * 如果以上步骤能找到搜索的起点，那么就用它们 否则的话，再去找质量可能不是那么好的起点
		 */
		if (candidateNodes.size() > 0)
			return candidateNodes;

		for (String queryWord : queryWordSet)
			for (long node : queryWord2Ids.get(queryWord)) {
				if (!typeSet.contains(node))
					continue;
				candidateNodes.add(node);
			}

		if (candidateNodes.size() > 0)
			return candidateNodes;

		for (String queryWord : queryWordSet)
			for (long node : queryWord2Ids.get(queryWord)) {
				candidateNodes.add(node);
			}

		return candidateNodes;
	}

	Set<Long> findAnchors() {

		Set<Long> anchors = new HashSet<>();

		for (String queryWord : queryWordSet) {
			Set<Long> nodes = queryWord2Ids.get(queryWord);
			Set<Long> types = new HashSet<>();
			for (long node : nodes)
				if (typeSet.contains(node))
					types.add(node);
			if (types.size() == 1)
				anchors.addAll(types);
		}

		return anchors;

	}

	double sumDist(long cNode, Set<Long> minDistNodeSet) {
		double r = 0;
		for (long node : minDistNodeSet) {
			double dist = dist(cNode, node);
			r += dist;
		}
		return r;
	}

	void queryWord2Ids(Set<String> queryWordSet) {
		for (String queryWord : queryWordSet) {
			queryWord2Ids.put(queryWord, new HashSet<>());
			for (long node : id2Name.keySet())
				if (id2Name.get(node).equals(queryWord))
					queryWord2Ids.get(queryWord).add(node);
		}
		queryWordSet.clear();
		for (String word : queryWord2Ids.keySet())
			if (queryWord2Ids.get(word).size() > 0)
				queryWordSet.add(word);
	}

	String stem(String word) {
		if (word.matches("\\w+")) {
			stemmer.setCurrent(word.toLowerCase());
			stemmer.stem();
			word = stemmer.getCurrent();
		}
		return word;
	}

	static List<String> camelSplit(String e) {
		List<String> r = new ArrayList<String>();
		Matcher m = Pattern.compile("^([a-z]+)|([A-Z][a-z]+)|([A-Z]+(?=([A-Z]|$)))").matcher(e);
		if (m.find()) {
			String s = m.group().toLowerCase();
			r.add(s);
			if (s.length() < e.length())
				r.addAll(camelSplit(e.substring(s.length())));
		}
		return r;
	}

	public double dist(long node1, long node2) {
		if (!id2Vec.containsKey(node1))
			return Double.MAX_VALUE;
		if (!id2Vec.containsKey(node2))
			return Double.MAX_VALUE;
		double r = 0;
		for (int i = 0; i < id2Vec.get(node1).size(); i++)
			r += (id2Vec.get(node1).get(i) - id2Vec.get(node2).get(i))
					* (id2Vec.get(node1).get(i) - id2Vec.get(node2).get(i));
		r = Math.sqrt(r);
		return r;
	}

}
