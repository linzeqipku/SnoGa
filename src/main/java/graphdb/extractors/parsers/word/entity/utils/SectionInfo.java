package graphdb.extractors.parsers.word.entity.utils;

import graphdb.extractors.parsers.word.corpus.Translator;

import java.io.IOException;
import java.util.List;

/**
 * Created by maxkibble on 2017/5/25.
 */
public class SectionInfo extends DocumentElementInfo {

    private String	title;
    private String  englishTitle;
    private int		layer;
    private String	sectionNumber;

    public String getTitle() {
        return title;
    }

    public String getEnglishTitle() { return englishTitle; }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setEnglishTitle(String title) {
        englishTitle = "";
        try {
            this.englishTitle = Translator.ch2en(title);
        }
        catch (IOException e) {
            System.out.println(title + " IOException in section title translation");
        }
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public String getSectionNumber() {
        return sectionNumber;
    }

    public void setSectionNumber(String sectionNumber) {
        this.sectionNumber = sectionNumber;
    }

    public String toString() {
        return this.getClass().getSimpleName() + " - { title: " + this.title + " }";
    }

    public String toEnglish() {
        StringBuilder ret = new StringBuilder();
        ret.append(getEnglishTitle());
        List<DocumentElementInfo> subElements = getSubElements();
        for(DocumentElementInfo subEle : subElements) {
            ret.append(subEle.toEnglish());
        }
        return ret.toString();
    }

    public String toHtml(boolean en) {
        StringBuilder html = new StringBuilder("<section>\n");
        html.append("<h" + getLayer() + ">" + getTitle() + "</h" + getLayer() + ">");
        List<DocumentElementInfo> subElements = getSubElements();
        for(DocumentElementInfo subEle : subElements) {
            html.append(subEle.toHtml(en));
        }
        html.append("</section>\n");
        return html.toString();
    }
}
