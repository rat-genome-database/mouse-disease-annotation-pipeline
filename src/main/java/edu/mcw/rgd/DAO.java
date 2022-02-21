package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.Ortholog;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.TermWithStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * @author mtutaj
 * wrapper to handle all DAO code
 */
public class DAO {

    Logger logInserted = LogManager.getLogger("inserted");
    Logger logDeleted = LogManager.getLogger("deleted");

    AnnotationDAO adao = new AnnotationDAO();
    GeneDAO geneDAO = new GeneDAO();
    OntologyXDAO odao = new OntologyXDAO();
    OrthologDAO orthologDAO = new OrthologDAO();
    XdbIdDAO xdao = new XdbIdDAO();

    public String getConnectionInfo() {
        return adao.getConnectionInfo();
    }

    public TermWithStats getTermWithStatsCached(String termAcc) throws Exception {
        return odao.getTermWithStatsCached(termAcc);
    }

    public List<Gene> getActiveGenesByXdbId(int xdbKey, String accId) throws Exception {
        return xdao.getActiveGenesByXdbId(xdbKey, accId);
    }

    public List<Annotation> getAnnotationsByReferenceAndEvidence(int refRgdId, String evidence) throws Exception {
        return adao.getAnnotationsByReferenceAndEvidence(refRgdId, evidence);
    }

    public int updateLastModified(int fullAnnotKey) throws Exception {
        return adao.updateLastModified(fullAnnotKey);
    }

    public int insertAnnotation(Annotation annot) throws Exception{
        int r = adao.insertAnnotation(annot);
        logInserted.debug(annot.dump("|"));
        return r;
    }

    public List<Annotation> getAnnotationsModifiedBeforeTimestamp(Date dt, int createdBy) throws Exception{
        return adao.getAnnotationsModifiedBeforeTimestamp(createdBy, dt, "D");
    }

    public int deleteAnnotations(List<Annotation> obsoleteAnnotations, int createdBy, Date cutoffDate) throws Exception {

        for( Annotation obsoleteAnnot: obsoleteAnnotations ) {
            logDeleted.debug("DELETE " + obsoleteAnnot.dump("|"));
        }

        return adao.deleteAnnotations(createdBy, cutoffDate);
    }

    /** load human TO (mouse,rat)  and  mouse TO (human,rat) orthologs
     *
     */
    public Map<Integer, List<Integer>> getOrthologs() throws Exception {

        Map<Integer, List<Integer>> results = new HashMap<>();

        merge(results, SpeciesType.HUMAN, SpeciesType.MOUSE);
        merge(results, SpeciesType.HUMAN, SpeciesType.RAT);
        merge(results, SpeciesType.MOUSE, SpeciesType.HUMAN);
        merge(results, SpeciesType.MOUSE, SpeciesType.RAT);

        return results;
    }

    void merge(Map<Integer, List<Integer>> results, int speciesTypeKey1, int speciesTypeKey2) throws Exception {

        for( Ortholog o: orthologDAO.getAllOrthologs(speciesTypeKey1, speciesTypeKey2) ) {
            List<Integer> rgdIds = results.get(o.getSrcRgdId());
            if( rgdIds==null ) {
                rgdIds = new ArrayList<>();
                results.put(o.getSrcRgdId(), rgdIds);
            }
            rgdIds.add(o.getDestRgdId());
        }
    }

    /** load map of rgd ids to Gene objects for rat/mouse/human
     */
    public Map<Integer, Gene> getGenes() throws Exception {

        Map<Integer, Gene> geneMap = new HashMap<>();
        for( Gene gene: geneDAO.getActiveGenes(SpeciesType.HUMAN) ) {
            geneMap.put(gene.getRgdId(), gene);
        }
        for( Gene gene: geneDAO.getActiveGenes(SpeciesType.MOUSE) ) {
            geneMap.put(gene.getRgdId(), gene);
        }
        for( Gene gene: geneDAO.getActiveGenes(SpeciesType.RAT) ) {
            geneMap.put(gene.getRgdId(), gene);
        }
        return geneMap;
    }
}