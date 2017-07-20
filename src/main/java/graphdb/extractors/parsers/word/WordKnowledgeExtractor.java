package graphdb.extractors.parsers.word;

import graphdb.extractors.parsers.word.document.DocumentParser;
import graphdb.extractors.parsers.word.entity.table.TableCellInfo;
import graphdb.extractors.parsers.word.entity.table.TableInfo;
import graphdb.extractors.parsers.word.entity.utils.*;
import graphdb.extractors.parsers.word.entity.word.WordDocumentInfo;
import graphdb.framework.Extractor;
import graphdb.framework.annotations.EntityDeclaration;
import graphdb.framework.annotations.PropertyDeclaration;
import graphdb.framework.annotations.RelationshipDeclaration;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import java.io.File;
import java.util.List;

/**
 * Created by maxkibble on 2017/5/26.
 */
public class WordKnowledgeExtractor implements Extractor {

    @EntityDeclaration
    public static final String DOCX_FILE = "DocxFile";
    @PropertyDeclaration(parent = DOCX_FILE)
    public static final String DOCX_NAME = "docxName";
    @PropertyDeclaration(parent = DOCX_FILE)
    public static final String ABSOLUTE_PATH = "absolutePath";
    @PropertyDeclaration(parent = DOCX_FILE)
    public static final String DOC_USEGE_TYPE = "usageType";
    @PropertyDeclaration(parent = DOCX_FILE)
    public static final String DOCX_PROJECT_NAME = "projectName";

    @EntityDeclaration
    public static final String DOCX_SECTION = "DocxSection";
    @PropertyDeclaration(parent = DOCX_SECTION)
    public static final String SECTION_TITLE = "sectionTitle";
    @PropertyDeclaration(parent = DOCX_SECTION)
    public static final String SECTION_LAYER = "sectionLayer";
    @PropertyDeclaration(parent = DOCX_SECTION)
    public static final String SECTION_NUMBER = "sectionNumber";
    @PropertyDeclaration(parent = DOCX_SECTION)
    public static final String SECTION_USAGE_TYPE = "sectionUsageType";
    @PropertyDeclaration(parent = DOCX_SECTION)
    public static final String SECTION_CONTENT = "sectionContent";
    @PropertyDeclaration(parent = DOCX_SECTION)
    public static final String SECTION_PACKAGE = "sectionPackage";
    @PropertyDeclaration(parent = DOCX_SECTION)
    public static final String SECTION_APIS = "sectionApis";
    @PropertyDeclaration(parent = DOCX_SECTION)
    public static final String SECTION_PROJECT_NAME = "sectionProject";

    @EntityDeclaration
    public static final String DOCX_TABLE = "DocxTable";
    @PropertyDeclaration(parent = DOCX_TABLE)
    public static final String TABLE_CAPTION = "tableCaption";
    @PropertyDeclaration(parent = DOCX_TABLE)
    public static final String TABLE_NUMBER = "tableNumber";
    @PropertyDeclaration(parent = DOCX_TABLE)
    public static final String TABLE_COLUMN_NUM = "tableColumnNum";
    @PropertyDeclaration(parent = DOCX_TABLE)
    public static final String TABLE_ROW_NUM = "tableRowNum";
    @PropertyDeclaration(parent = DOCX_TABLE)
    public static final String TABLE_CONTENT = "tableContent";

    @PropertyDeclaration
    public static final String DOCX_PLAIN_TEXT = "DocxPlainText";
    @PropertyDeclaration(parent = DOCX_PLAIN_TEXT)
    public static final String PLAIN_TEXT_CONTENT = "plainTextContent";

    @RelationshipDeclaration
    public static final String HAVE_SUB_ELEMENT = "have_sub_element";

    String docxDirPath = "";
    GraphDatabaseService db = null;

    public void setDocxFilePath(String docxFilePath) {
        this.docxDirPath = docxFilePath;
    }

    public void dfs(WordDocumentInfo doc) {
        Node node = db.createNode();
        GraphNodeUtil.createDocumentNode(doc, node);
        for(DocumentElementInfo elementInfo : doc.getSubElements()) {
            Node subNode = dfs_ele(elementInfo);
            node.createRelationshipTo(subNode, RelationshipType.withName(HAVE_SUB_ELEMENT));
        }
    }

    public Node dfs_ele(DocumentElementInfo ele) {
        Node node = db.createNode();
        if(ele == null) {
            System.out.println("Meet a null element in docx file:"
                    + docxDirPath + "\n This result in a empty node created");
            return node;
        }

        if(ele instanceof PlainTextInfo) {
            GraphNodeUtil.createPlainTextNode((PlainTextInfo) ele, node);
        }
        else if(ele instanceof SectionInfo) {
            GraphNodeUtil.createSectionNode((SectionInfo) ele, node);
            List<DocumentElementInfo> subElements = ele.getSubElements();
            for(DocumentElementInfo subEle : subElements) {
                Node subNode = dfs_ele(subEle);
                String currentSectionContent = (String) node.getProperty(SECTION_CONTENT);
                if(subEle instanceof SectionInfo) {
                    currentSectionContent = currentSectionContent +
                            "\n" + subNode.getProperty(SECTION_CONTENT);
                }
                else if(subEle instanceof TableInfo) {
                    currentSectionContent = currentSectionContent +
                            "\n" + subNode.getProperty(TABLE_CONTENT);
                }
                else if(subEle instanceof PlainTextInfo) {
                    currentSectionContent = currentSectionContent +
                            "\n" + subNode.getProperty(PLAIN_TEXT_CONTENT);
                }
                node.setProperty(SECTION_CONTENT, currentSectionContent);
                node.createRelationshipTo(subNode, RelationshipType.withName(HAVE_SUB_ELEMENT));
            }
        }
        else if(ele instanceof TableInfo) {
            GraphNodeUtil.createTableNode((TableInfo) ele, node);

            String tableContent = "";
            List<DocumentElementInfo> rows = ele.getSubElements();
            for(DocumentElementInfo row:rows) {
                List<DocumentElementInfo> cellsInARow = row.getSubElements();
                for (DocumentElementInfo cell : cellsInARow) {
                    if (cell instanceof TableCellInfo) {
                        TableCellInfo cellInfo = (TableCellInfo) cell;
                        PlainTextInfo textCell = (PlainTextInfo) cellInfo.getSubElements().get(0);
                        tableContent = tableContent + textCell.getText() + "\t";
                    }
                }
                tableContent = tableContent + "\n";
            }
            node.setProperty(TABLE_CONTENT, tableContent);
        }
        return node;
    }

    public void parseDocxFile(String path) {
        File file = new File(path);
        if(file == null || !file.getName().toLowerCase().endsWith(".docx"))
            return;

        // Parse docx file
        DocumentInfo doc = DocumentParser.parseFileToDocumentInfo(file);
        if(!(doc instanceof WordDocumentInfo)) {
            //System.out.println("Not a docx file: " + path);
            return;
        }
        //System.out.println("Parse docx file finished: " + path);

        // Build graph
        //System.out.println("Begin to insert nodes to graph");
        try (Transaction tx = db.beginTx()) {
            dfs((WordDocumentInfo) doc);
            tx.success();
            tx.close();
        }
        //System.out.println("Insertion finished: " + path);
        //System.out.println("----------------------------");
    }

    public void traverseFolder(String path) {
        File file = new File(path);
        if (!file.exists()) return;
        if (!file.isDirectory()) {
            parseDocxFile(path);
            return;
        }
        File[] files = file.listFiles();
        for (File file2 : files) {
            if (file2.isDirectory())
                traverseFolder(file2.getAbsolutePath());
            else
                parseDocxFile(file2.getAbsolutePath());
        }
    }

    public void run(GraphDatabaseService db) {
        // Initialize graph db
        this.db = db;
        traverseFolder(docxDirPath);
    }
}