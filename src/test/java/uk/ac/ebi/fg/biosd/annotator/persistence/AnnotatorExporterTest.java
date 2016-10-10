package uk.ac.ebi.fg.biosd.annotator.persistence;

import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.junit.*;
import uk.ac.ebi.fg.biosd.annotator.AnnotatorResources;
import uk.ac.ebi.fg.biosd.annotator.PropertyValAnnotationManager;
import uk.ac.ebi.fg.biosd.annotator.purge.Purger;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.expgraph.properties.Unit;
import uk.ac.ebi.fg.core_model.expgraph.properties.UnitDimension;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;
import uk.ac.ebi.fg.core_model.xref.ReferenceSource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

import static org.junit.Assert.*;

/**
 * Created by olgavrou on 16/05/2016.
 */
public class AnnotatorExporterTest {

    private static ExperimentalPropertyValue<ExperimentalPropertyType> pval1;
    private static ExperimentalPropertyValue<ExperimentalPropertyType> pval2;
    private static ExperimentalPropertyValue<ExperimentalPropertyType> pval3;
    private static ExperimentalPropertyValue<ExperimentalPropertyType> pval4;
    private static ArrayList<ExperimentalPropertyValue<ExperimentalPropertyType>> pvals;



    @BeforeClass
    public static void setup() {

        // ---- First let's create some examples, see below for the code relevant to AnnotatorAccessor
        //
        ExperimentalPropertyType ptype = new ExperimentalPropertyType("disease");
        pval1 = new ExperimentalPropertyValue<>("asthma", ptype);

        ReferenceSource src = new ReferenceSource("DOID", null);
        OntologyEntry oe = new OntologyEntry("4", src); //giving a suggestion for ontology term for disease instead of asthma, to show the difference in comparing the annotations
        pval1.addOntologyTerm(oe);

        // A numeric value with a unit
        pval2 = new ExperimentalPropertyValue<ExperimentalPropertyType>("120", new ExperimentalPropertyType("Treatment Temperature"));
        pval2.setUnit(new Unit("degree Celsius", new UnitDimension("Temperature")));


        ptype = new ExperimentalPropertyType ( "specie" );
        pval3 = new ExperimentalPropertyValue<> ( "homo sapiens", ptype );

        ptype = new ExperimentalPropertyType ( "Organism");
        pval4 = new ExperimentalPropertyValue<> ( "Arabidopsis thaliana", ptype );

        src = new ReferenceSource("NCBITaxon", null);
        oe = new OntologyEntry("3702", src); //giving a suggestion for ontology term for disease instead of asthma, to show the difference in comparing the annotations
        pval4.addOntologyTerm(oe);

        // This is the annotation creation part, normally done by the annotator, during its periodic scheduled run
        // Keep going down
        PropertyValAnnotationManager annMgr = AnnotatorResources.getInstance().getPvAnnMgr();
        annMgr.annotate(pval1);
        annMgr.annotate(pval2);
        annMgr.annotate(pval3);
        annMgr.annotate(pval4);

        new AnnotatorPersister().persist();

        // Do this in the unlikely case you first annotated, then use the access API. The annotator leaves around resources
        // that the accessor shouldn't see.
        AnnotatorResources.getInstance().reset();

        pvals = new ArrayList<>();
        pvals.add(pval2);
        pvals.add(pval3);
        pvals.add(pval4);
    }

    @Test
    public void testEmptyFile() throws IOException {
        AnnotatorExporter annotatorExporter = new AnnotatorExporter();

        try {
            annotatorExporter.compareDiscoveredAndResolvedForMSI("testAccession", pvals, "./target");

        } catch (IOException e) {
            throw new IOException("File Problem");
        }

        File file = new File("./target/CompareResolvedWithDiscovered.txt");

        assertTrue("File is empty!", FileUtils.readFileToString(file).isEmpty());

    }

    @Test
    //testing to see that the annotations are printed correctly
    public void testLocation() throws IOException {

        AnnotatorExporter annotatorExporter = new AnnotatorExporter();

        pvals.add(pval1);

        try {
            annotatorExporter.compareDiscoveredAndResolvedForMSI("testAccession", pvals, "./target");
            annotatorExporter.printAllOntologyAnnotationsForMSI("testAccession", pvals, "./target");

        } catch (IOException e) {
            throw new IOException("File Problem");
        }

        //lets see how the annotations got printed
        File file = new File("./target/Acc_testAccession");

        assertTrue("Annotation not found!", FileUtils.readFileToString(file).contains("EFO_0000270"));
        assertTrue("Annotation not found!", FileUtils.readFileToString(file).contains("DOID_4"));
        assertTrue("Annotation not found!", FileUtils.readFileToString(file).contains("NCBITaxon_9606"));
        assertTrue("Annotation not found!", FileUtils.readFileToString(file).contains("NCBITaxon_3702"));


        Scanner scanner = new Scanner(file);

        while (scanner.hasNext()){
            String line = scanner.nextLine();
            if (line.contains("120 Property")){
                line = scanner.nextLine();
                assertEquals("Not Empty String, Annotation found!", "", line);
            }
        }

        file = new File("./target/CompareResolvedWithDiscovered.txt");

        boolean discovered = false;
        boolean resolved = false;
        discovered = FileUtils.readFileToString(file).contains("Discovered");
        resolved = FileUtils.readFileToString(file).contains("Resolved");
        assertTrue("Although annotations are different, not found in compare! CompareResolvedWithDiscovered.txt could be empty!", discovered);
        assertTrue("Although annotations are different, not found in compare! CompareResolvedWithDiscovered.txt could be empty!", resolved);

        assertTrue("File is empty!", !FileUtils.readFileToString(file).isEmpty());

    }

    @AfterClass
    public static void cleanUp ()
    {
        new Purger().purge ( new DateTime().minusMinutes ( 2 ).toDate (), new Date() );
        File file = new File("./target/CompareResolvedWithDiscovered.txt");
        file.delete();
        file = new File("./target/Acc_testAccession");
        file.delete();
    }

}
