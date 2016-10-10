package uk.ac.ebi.fg.biosd.annotator.purge;

import org.hibernate.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ebi.arrayexpress2.magetab.exception.ParseException;
import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.cli.AnnotateCmd;
import uk.ac.ebi.fg.biosd.annotator.test.AnnotatorResourcesResetRule;
import uk.ac.ebi.fg.biosd.annotator.threading.PropertyValAnnotationService;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.biosd.sampletab.loader.Loader;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Persister;
import uk.ac.ebi.fg.biosd.sampletab.persistence.Unloader;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;


/**
 * Created by olgavrou on 22/04/2016.
 */
public class PurgerTest {

    private PropertyValAnnotationService annotationService;
    private EntityManager entityManager;

    private Logger log = LoggerFactory.getLogger ( this.getClass () );

    static {
        System.setProperty ( AnnotateCmd.NO_EXIT_PROP, "true" );
    }

    @Rule
    public TestRule resResetRule = new AnnotatorResourcesResetRule();



    @Before
    public void initResources () {
        AnnotatorResources.getInstance ().reset ();
        annotationService = new PropertyValAnnotationService();
        EntityManagerFactory emf = Resources.getInstance ().getEntityManagerFactory ();
        entityManager = emf.createEntityManager ();
    }


    @Test
    @Transactional
    @Rollback(true)
    public void testPurgePV() throws ParseException {

        //submit dummy sample first
        URL sampleTabUrl = getClass().getClassLoader().getResource( "GAE-MTAB-27_truncated.sampletab.csv" );

        Loader loader = new Loader ();
        MSI msi = loader.fromSampleData ( sampleTabUrl );
        Persister persister = new Persister ();
        persister.persist ( msi );


        EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
        AccessibleDAO<MSI> dao = new AccessibleDAO<> ( MSI.class, em );

        msi = dao.find ( msi.getAcc () );
        ArrayList<ExperimentalPropertyValue<ExperimentalPropertyType>> propertyValues = annotationService.getPropertyValuesOfMSI(msi);

        //Assert that there aren't any annotations on the atrribute values
        //both from plain text and suggested ontology terms

        for (ExperimentalPropertyValue<ExperimentalPropertyType> propertyValue : propertyValues) {

            ArrayList actual = queryPVAnnotations(getSourceTextFromPV(propertyValue));
            Assert.assertEquals("Expected empty array!", 0, actual.size());

            ArrayList<String> sourceTexts = getSourceTextsFromOE(propertyValue);
            for (String sourceText : sourceTexts) {
                actual = queryResOntAnnotations(sourceText);
                Assert.assertEquals("Expected empty array!", 0, actual.size());
            }

        }


        //annotate and then test again
        AnnotateCmd.main ( "--submission", msi.getAcc () );



        ArrayList actualPVAnn = new ArrayList();
        ArrayList actualOntAnn = new ArrayList();
        for (ExperimentalPropertyValue<ExperimentalPropertyType> propertyValue : propertyValues) {

            actualPVAnn.addAll(queryPVAnnotations(getSourceTextFromPV(propertyValue)));

            ArrayList<String> sourceTexts = getSourceTextsFromOE(propertyValue);
            for (String sourceText : sourceTexts) {
                actualOntAnn.addAll(queryResOntAnnotations(sourceText));
            }

        }
        Assert.assertNotSame("Expected full array!", 0, actualPVAnn.size());
        Assert.assertNotSame("Expected full array!", 0, actualOntAnn.size());


        //Purge and assert again
        Purger purger = new Purger();

            for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : propertyValues) {
                purger.purgePVAnnotations(pv);
                purger.purgeResolvedOntTerms(pv);
            }


        for (ExperimentalPropertyValue<ExperimentalPropertyType> propertyValue : propertyValues) {

                ArrayList actual = queryPVAnnotations(getSourceTextFromPV(propertyValue));
                Assert.assertEquals("Expected empty array!", 0, actual.size());

                ArrayList<String> sourceTexts = getSourceTextsFromOE(propertyValue);
                for (String sourceText : sourceTexts) {
                    actual = queryResOntAnnotations(sourceText);
                    Assert.assertEquals("Expected empty array!", 0, actual.size());
                }

            }

        // Remove the submission
        //
        Unloader unloader = new Unloader ();
        unloader.setDoPurge ( true );
        unloader.unload ( msi );

        }



    public ArrayList queryPVAnnotations(String sourceText){
        String hql = "FROM ExpPropValAnnotation ann WHERE\n"
                + "source_text = :sourceText\n";

        Session session = (Session) this.entityManager.getDelegate();

        Query q = session.createQuery(hql)
                .setParameter("sourceText", sourceText);


        return queryDatabaseForAnnotations(q);
    }

    public  ArrayList queryResOntAnnotations(String sourceText){

        String hql= "FROM ResolvedOntoTermAnnotation ann WHERE\n"
                + "source_text = :sourceText\n";

        Session session = (Session) this.entityManager.getDelegate ();

        Query q = session.createQuery ( hql )
                .setParameter("sourceText", sourceText);

        return queryDatabaseForAnnotations ( q );

        /*hql = "FROM ComputedOntoTerm WHERE uri NOT IN ( SELECT ontoTermUri FROM ResolvedOntoTermAnnotation )";
        q = session.createQuery ( hql );

        queryDatabaseForAnnotations ( q);*/

    }

    public ArrayList queryDatabaseForAnnotations(Query qry){

        ArrayList<Object> foundAnnotations = new ArrayList();

        EntityTransaction tx = entityManager.getTransaction ();
        tx.begin ();

        qry
                .setReadOnly ( true )
                .setFetchSize ( 10000 )
                .setCacheMode ( CacheMode.IGNORE );

        for (ScrollableResults annRs = qry.scroll ( ScrollMode.FORWARD_ONLY ); annRs.next (); ) {
            foundAnnotations.add(annRs.get(0));
        }

        tx.commit ();
        return foundAnnotations;
    }

    public String getSourceTextFromPV(ExperimentalPropertyValue<ExperimentalPropertyType> propertyValue){
        //search for pv annotations
        String sourceText = null;
        String type = propertyValue.getType().getTermText();
        if (type != null && !type.equals("")) {
            sourceText = type + "|" + propertyValue.getTermText();
        }
        return sourceText;
    }

    public ArrayList<String> getSourceTextsFromOE(ExperimentalPropertyValue<ExperimentalPropertyType> propertyValue) {
        ArrayList<String> returnlist = new ArrayList<>();
        Set<OntologyEntry> oes = propertyValue.getOntologyTerms();
        String sourceText = null;
        if (oes != null) {
            String acc;
            String ontologyPrefix;
            for (OntologyEntry oe : oes) {
                sourceText = null;
                acc = null;
                ontologyPrefix = null;

                acc = oe.getAcc();
                ReferenceSource source = oe.getSource();
                ontologyPrefix = source.getAcc();
                if (ontologyPrefix == null) {
                    ontologyPrefix = source.getName();
                }
                returnlist.add(sourceText = ontologyPrefix + "|" + acc);
            }
        }
        return returnlist;
    }
}


