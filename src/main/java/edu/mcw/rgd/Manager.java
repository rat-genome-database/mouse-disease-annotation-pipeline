package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author mtutaj
 * @since 05/15/2018
 */
public class Manager {

    Logger logStatus = Logger.getLogger("status");

    private DAO dao = new DAO();
    private String version;
    private String sourcePipeline;
    private String remoteDoFile;
    private String localDoFile;
    private String evidenceCode;
    private int refRgdId;
    private int createdBy;
    private int refRgdIdOmimPipeline;
    private int refRgdIdCtdPipeline;
    private String evidenceCodeOmimPipeline;
    private String evidenceCodeCtdPipeline;
    private String staleAnnotDeleteThreshold;
    private String evidenceCode2;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Manager manager = (Manager) (bf.getBean("manager"));
        manager.logStatus.info(manager.getVersion());

        try {
            manager.run();
        }catch (Exception e) {
            manager.logStatus.error(e);
            throw e;
        }
    }

    public void run() throws Exception {
        long time0 = System.currentTimeMillis();
        Date date0 = new Date();

        logStatus.info(dao.getConnectionInfo());

        int originalAnnotCount = dao.getAnnotationsModifiedBeforeTimestamp(date0, getCreatedBy()).size();
        logStatus.info("ANNOT COUNT ORIGINAL: "+originalAnnotCount);

        String localFileName = downloadRemoteFile();

        List<Record> records = loadFile(localFileName);

        qc(records);

        load(records);

        deleteStaleAnnotations(date0, originalAnnotCount, getStaleAnnotDeleteThreshold());

        logStatus.info("=== OK === elapsed time "+ Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    String downloadRemoteFile() throws Exception {

        FileDownloader downloader = new FileDownloader();
        downloader.setExternalFile(getRemoteDoFile());
        downloader.setLocalFile(getLocalDoFile());
        downloader.setUseCompression(true);
        downloader.setPrependDateStamp(true);
        return downloader.downloadNew();
    }

    final String EXPECTED_HEADER_LINE = "DO Disease ID\tDO Disease Name\tOMIM IDs\tHomoloGene ID\tCommon Organism Name\tNCBI Taxon ID\tSymbol\tEntrezGene ID\tMouse MGI ID";

    List<Record> loadFile(String localFileName) throws Exception {
        List<Record> records = new ArrayList<>();

        try( BufferedReader in = Utils.openReader(localFileName) ) {

            // validate header line
            String headerLine = in.readLine();
            if( !headerLine.equals(EXPECTED_HEADER_LINE) ) {
                throw new Exception("Exception: Unexpected header line");
            }

            String line;
            while( (line=in.readLine())!=null ) {
                Record rec = new Record();
                rec.line = line;
                records.add(rec);
            }
        }

        logStatus.info("lines loaded: "+records.size());
        return records;
    }

    void qc(List<Record> records) throws Exception {

        // load annotations in rgd for Mouse Do pipeline
        Map<String, Integer> mouseDoAnnotsInRgd = getTermAccAndObjectRgdIdsForAnnotations(getRefRgdId(), getEvidenceCode());
        logStatus.info("annotations in RGD for MouseDiseasePipeline, evidence "+getEvidenceCode()+": "+mouseDoAnnotsInRgd.size());

        Map<String, Integer> mouseDoAnnotsInRgdIss = getTermAccAndObjectRgdIdsForAnnotations(getRefRgdId(), getEvidenceCode2());
        logStatus.info("annotations in RGD for MouseDiseasePipeline, evidence "+getEvidenceCode2()+": "+mouseDoAnnotsInRgdIss.size());

        Set<String> omimDoAnnotsInRgd = getTermAccAndObjectRgdIdsForAnnotations(getRefRgdIdOmimPipeline(), getEvidenceCodeOmimPipeline()).keySet();
        logStatus.info("annotations in RGD for OmimDiseasePipeline, evidence "+getEvidenceCodeOmimPipeline()+": "+omimDoAnnotsInRgd.size());

        Set<String> ctdDoAnnotsInRgd = getTermAccAndObjectRgdIdsForAnnotations(getRefRgdIdCtdPipeline(), getEvidenceCodeCtdPipeline()).keySet();
        logStatus.info("annotations in RGD for CTDDiseasePipeline, evidence "+getEvidenceCodeCtdPipeline()+": "+ctdDoAnnotsInRgd.size());

        Map<Integer, List<Integer>> orthologs = dao.getOrthologs();
        logStatus.info("rat-mouse-human orthologs loaded");

        Map<Integer, Gene> geneMap = dao.getGenes();
        logStatus.info("rat-mouse-human genes loaded: "+geneMap.size());


        AtomicInteger[] counters = new AtomicInteger[19];
        for( int i=0; i<counters.length; i++ ) {
            counters[i] = new AtomicInteger(0);
        }

        records.parallelStream().forEach(rec -> {
            String[] cols = rec.line.split("[\\t]", -1);
            String doId = cols[0];
            String omimIds = cols[2].replace("|", " | ").intern();
            String egId = cols[7];
            String mgiId = cols[8];
            try {
                rec.term = dao.getTermWithStatsCached(doId);

                // try to match gene by mgi id
                if( !Utils.isStringEmpty(mgiId) ) {
                    List<Gene> genes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_MGD, mgiId);
                    if( genes.size()==1 ) {
                        rec.gene = genes.get(0);
                    }
                }

                // try to match gene by eg id
                if( rec.gene==null ) {
                    List<Gene> genes = dao.getActiveGenesByXdbId(XdbId.XDB_KEY_NCBI_GENE, egId);
                    if( genes.size()==1 ) {
                        rec.gene = genes.get(0);
                    }
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }

            if( rec.term!=null ) {
                counters[0].incrementAndGet();
            }
            if( rec.gene!=null ) {
                counters[1].incrementAndGet();
            }
            if( rec.gene!=null && rec.term!=null ) {
                counters[2].incrementAndGet();

                if( rec.gene.getSpeciesTypeKey()== SpeciesType.MOUSE ) {
                    counters[3].incrementAndGet();
                }

                // see if the incoming annotation is the same as OMIM annotation
                String key = rec.term.getAccId()+"|"+rec.gene.getRgdId();
                if( omimDoAnnotsInRgd.contains(key) ) {
                    counters[4].incrementAndGet();
                    return;
                }

                // see if the incoming annotation is the same as CTD annotation
                if( ctdDoAnnotsInRgd.contains(key) ) {
                    counters[5].incrementAndGet();
                    return;
                }

                counters[6].incrementAndGet();
                if( rec.gene.getSpeciesTypeKey()== SpeciesType.MOUSE ) {
                    counters[7].incrementAndGet();
                }
                if( rec.gene.getSpeciesTypeKey()== SpeciesType.HUMAN ) {
                    counters[8].incrementAndGet();
                }

                // create incoming annotation
                Annotation a = new Annotation();
                a.setCreatedBy(getCreatedBy());
                a.setRefRgdId(getRefRgdId());
                a.setLastModifiedBy(getCreatedBy());
                a.setAspect("D"); // for DO terms
                a.setTermAcc(rec.term.getAccId());
                a.setTerm(rec.term.getTerm());
                a.setAnnotatedObjectRgdId(rec.gene.getRgdId());
                a.setRgdObjectKey(RgdId.OBJECT_KEY_GENES);
                a.setObjectSymbol(rec.gene.getSymbol());
                a.setObjectName(rec.gene.getName());
                a.setSpeciesTypeKey(rec.gene.getSpeciesTypeKey());
                a.setDataSrc(getSourcePipeline());
                a.setEvidence(getEvidenceCode());
                a.setNotes(omimIds);
                a.setCreatedDate(new Date());
                a.setLastModifiedDate(a.getCreatedDate());
                rec.annotsIncoming.add(a);

                // is the incoming annotation already in RGD
                Integer annotKey = mouseDoAnnotsInRgd.get(key);
                if( annotKey!=null ) {
                    a.setKey(annotKey);
                    counters[9].incrementAndGet();
                } else {
                    counters[10].incrementAndGet();
                }

                // handle ortholog annotations
                List<Integer> orthos = orthologs.get(rec.gene.getRgdId());
                if( orthos==null ) {
                    return;
                }
                for( int geneRgdId: orthos ) {

                    Gene gene = geneMap.get(geneRgdId);

                    // see if the incoming annotation is the same as OMIM annotation
                    String key2 = rec.term.getAccId()+"|"+geneRgdId;
                    if( omimDoAnnotsInRgd.contains(key2) ) {
                        counters[11].incrementAndGet();
                        return;
                    }

                    // see if the incoming annotation is the same as CTD annotation
                    if( ctdDoAnnotsInRgd.contains(key2) ) {
                        counters[12].incrementAndGet();
                        return;
                    }

                    counters[13].incrementAndGet();
                    if( gene.getSpeciesTypeKey()== SpeciesType.MOUSE ) {
                        counters[16].incrementAndGet();
                    }
                    if( gene.getSpeciesTypeKey()== SpeciesType.HUMAN ) {
                        counters[17].incrementAndGet();
                    }
                    if( gene.getSpeciesTypeKey()== SpeciesType.RAT ) {
                        counters[18].incrementAndGet();
                    }

                    // create incoming annotation
                    Annotation a2 = new Annotation();
                    a2.setCreatedBy(getCreatedBy());
                    a2.setRefRgdId(getRefRgdId());
                    a2.setLastModifiedBy(getCreatedBy());
                    a2.setAspect("D"); // for DO terms
                    a2.setTermAcc(rec.term.getAccId());
                    a2.setTerm(rec.term.getTerm());
                    a2.setAnnotatedObjectRgdId(gene.getRgdId());
                    a2.setWithInfo("RGD:"+rec.gene.getRgdId());
                    a2.setRgdObjectKey(RgdId.OBJECT_KEY_GENES);
                    a2.setObjectSymbol(gene.getSymbol());
                    a2.setObjectName(gene.getName());
                    a2.setSpeciesTypeKey(gene.getSpeciesTypeKey());
                    a2.setDataSrc(getSourcePipeline());
                    a2.setEvidence(getEvidenceCode2());
                    a2.setNotes(omimIds);
                    a2.setCreatedDate(new Date());
                    a2.setLastModifiedDate(a.getCreatedDate());
                    rec.annotsIncoming.add(a2);

                    // is the incoming annotation already in RGD
                    Integer annotKey2 = mouseDoAnnotsInRgdIss.get(key2);
                    if( annotKey2!=null ) {
                        a2.setKey(annotKey2);
                        counters[14].incrementAndGet();
                    } else {
                        counters[15].incrementAndGet();
                    }
                }
            }
        });

        logStatus.info("lines with valid DO terms: "+counters[0].get());
        logStatus.info("lines with valid genes: "+counters[1].get());
        logStatus.info("lines with valid DO terms and genes: "+counters[2].get());
        logStatus.info("  out of which are for MOUSE       : "+counters[3].get());
        logStatus.info("===");
        logStatus.info("IEA annotations skipped, same as OMIM: "+counters[4].get());
        logStatus.info("IEA annotations skipped, same as CTD: "+counters[5].get());
        logStatus.info("IEA annotations incoming: MouseDO : "+counters[6].get());
        logStatus.info("  out of which are for MOUSE  : "+counters[7].get());
        logStatus.info("  out of which are for HUMAN  : "+counters[8].get());
        logStatus.info("IEA MouseDO annotations already in RGD: "+counters[9].get());
        logStatus.info("IEA MouseDO annotations inserted: "+counters[10].get());
        logStatus.info("===");
        logStatus.info("ISS annotations skipped, same as OMIM: "+counters[11].get());
        logStatus.info("ISS annotations skipped, same as CTD: "+counters[12].get());
        logStatus.info("ISS annotations incoming: MouseDO : "+counters[13].get());
        logStatus.info("  out of which are for MOUSE  : "+counters[16].get());
        logStatus.info("  out of which are for HUMAN  : "+counters[17].get());
        logStatus.info("  out of which are for RAT    : "+counters[18].get());
        logStatus.info("ISS MouseDO annotations already in RGD: "+counters[14].get());
        logStatus.info("ISS MouseDO annotations inserted: "+counters[15].get());
    }

    Map<String, Integer> getTermAccAndObjectRgdIdsForAnnotations(int refRgdId, String evidence) throws Exception {
        List<Annotation> annots = dao.getAnnotationsByReferenceAndEvidence(refRgdId, evidence);
        Map<String,Integer> result = new HashMap<>();
        for( Annotation a: annots ) {
            String key = a.getTermAcc()+"|"+a.getAnnotatedObjectRgdId();
            result.put(key, a.getKey());
        }
        return result;
    }

    void load( List<Record> records ) {

        records.parallelStream().forEach(rec -> {

            try {
                for( Annotation a: rec.annotsIncoming ) {
                    if( a.getKey() != null ) {
                        dao.updateLastModified(a.getKey());
                    } else {
                        dao.insertAnnotation(a);
                    }
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    void deleteStaleAnnotations(Date time0, int originalAnnotCount, String staleAnnotDeleteThresholdStr) throws Exception {

        int staleAnnotDeleteThresholdPerc = Integer.parseInt(staleAnnotDeleteThresholdStr.substring(0, staleAnnotDeleteThresholdStr.length()-1));
        int staleAnnotDeleteThresholdCount = (staleAnnotDeleteThresholdPerc*originalAnnotCount) / 100;
        logStatus.info("OBSOLETE ANNOTATION "+staleAnnotDeleteThresholdStr+" DELETE THRESHOLD: "+ staleAnnotDeleteThresholdCount);

        List<Annotation> obsoleteAnnotations = dao.getAnnotationsModifiedBeforeTimestamp(time0, getCreatedBy());
        logStatus.info("OBSOLETE ANNOTATION COUNT "+ obsoleteAnnotations.size());

        if( obsoleteAnnotations.size() > staleAnnotDeleteThresholdCount ) {
            String msg = "WARN: OBSOLETE ANNOTATIONS NOT DELETED: "+staleAnnotDeleteThresholdStr+" THRESHOLD VIOLATED! - ";
            logStatus.info(msg);
            if( originalAnnotCount!=0 ) {
                logStatus.info(msg + staleAnnotDeleteThresholdCount);
            }
            return;
        }

        dao.deleteAnnotations(obsoleteAnnotations, getCreatedBy(), time0);
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setSourcePipeline(String sourcePipeline) {
        this.sourcePipeline = sourcePipeline;
    }

    public String getSourcePipeline() {
        return sourcePipeline;
    }

    public void setRemoteDoFile(String remoteDoFile) {
        this.remoteDoFile = remoteDoFile;
    }

    public String getRemoteDoFile() {
        return remoteDoFile;
    }

    public void setLocalDoFile(String localDoFile) {
        this.localDoFile = localDoFile;
    }

    public String getLocalDoFile() {
        return localDoFile;
    }

    public void setEvidenceCode(String evidenceCode) {
        this.evidenceCode = evidenceCode;
    }

    public String getEvidenceCode() {
        return evidenceCode;
    }

    public void setRefRgdId(int refRgdId) {
        this.refRgdId = refRgdId;
    }

    public int getRefRgdId() {
        return refRgdId;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setRefRgdIdOmimPipeline(int refRgdIdOmimPipeline) {
        this.refRgdIdOmimPipeline = refRgdIdOmimPipeline;
    }

    public int getRefRgdIdOmimPipeline() {
        return refRgdIdOmimPipeline;
    }

    public void setRefRgdIdCtdPipeline(int refRgdIdCtdPipeline) {
        this.refRgdIdCtdPipeline = refRgdIdCtdPipeline;
    }

    public int getRefRgdIdCtdPipeline() {
        return refRgdIdCtdPipeline;
    }

    public void setEvidenceCodeOmimPipeline(String evidenceCodeOmimPipeline) {
        this.evidenceCodeOmimPipeline = evidenceCodeOmimPipeline;
    }

    public String getEvidenceCodeOmimPipeline() {
        return evidenceCodeOmimPipeline;
    }

    public void setEvidenceCodeCtdPipeline(String evidenceCodeCtdPipeline) {
        this.evidenceCodeCtdPipeline = evidenceCodeCtdPipeline;
    }

    public String getEvidenceCodeCtdPipeline() {
        return evidenceCodeCtdPipeline;
    }

    public void setStaleAnnotDeleteThreshold(String staleAnnotDeleteThreshold) {
        this.staleAnnotDeleteThreshold = staleAnnotDeleteThreshold;
    }

    public String getStaleAnnotDeleteThreshold() {
        return staleAnnotDeleteThreshold;
    }

    public void setEvidenceCode2(String evidenceCode2) {
        this.evidenceCode2 = evidenceCode2;
    }

    public String getEvidenceCode2() {
        return evidenceCode2;
    }
}
