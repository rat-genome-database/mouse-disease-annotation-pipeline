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

    public String line;

    public Term term; // DO ontology term, if available
    public Gene gene; // gene in rgd
    public List<Annotation> annotsIncoming = new ArrayList<>();
}
