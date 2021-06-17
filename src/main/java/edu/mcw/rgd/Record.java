package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mtutaj on 5/15/2018.
 * Incoming data record
 */
public class Record {

    // incoming data
    public String doTermAcc; // f.e. DOID:0060575
    public String doTermName; // f.e. 3MC syndrome 1
    public String omimIds; // f.e. OMIM:257920
    public String commonOrganismName; // f.e. human
    public String ncbiTaxonId; // f.e. 9606
    public String symbol; // f.e. MASP1
    public String egId; // f.e. 5648
    public String mgiId;

    public Term term; // DO ontology term, if available
    public Gene gene; // gene in rgd
    public List<Annotation> annotsIncoming = new ArrayList<>();
}
