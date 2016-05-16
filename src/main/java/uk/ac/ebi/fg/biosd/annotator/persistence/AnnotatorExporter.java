package uk.ac.ebi.fg.biosd.annotator.persistence;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import uk.ac.ebi.fg.biosd.annotator.model.ComputedOntoTerm;
import uk.ac.ebi.fg.biosd.annotator.model.ExpPropValAnnotation;
import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.biosd.annotator.threading.PropertyValAnnotationService;
import uk.ac.ebi.fg.biosd.model.organizational.MSI;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyType;
import uk.ac.ebi.fg.core_model.expgraph.properties.ExperimentalPropertyValue;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AccessibleDAO;
import uk.ac.ebi.fg.core_model.resources.Resources;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;

import javax.persistence.EntityManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by olgavrou on 13/05/2016.
 */
public class AnnotatorExporter {


    private ExpPropValAnnotation getExpPropValAnnotation(ExperimentalPropertyValue<ExperimentalPropertyType> pv, AnnotatorAccessor annotatorAccessor) {
        List<ExpPropValAnnotation> discovered = annotatorAccessor.getExpPropValAnnotations(pv);
        if (discovered != null && discovered.size() != 0) {
            for (ExpPropValAnnotation pvAnn : discovered) {
                return pvAnn;
            }
        }
        return null;
    }

    private ResolvedOntoTermAnnotation getResolvedOntoTermAnnotation(ExperimentalPropertyValue<ExperimentalPropertyType> pv, AnnotatorAccessor annotatorAccessor) {

        List<Pair<ComputedOntoTerm, ResolvedOntoTermAnnotation>> resolved = new ArrayList<>();
        for (OntologyEntry oe : pv.getOntologyTerms()) {

            resolved.add(annotatorAccessor.getResolvedOntoTerm(oe));
        }

        if (resolved != null && resolved.size() != 0) {
            for (Pair<ComputedOntoTerm, ResolvedOntoTermAnnotation> oe : resolved) {
                if (oe != null) {
                    return oe.getRight();
                }
            }
        }
        return null;
    }

    public void compareDiscoveredAndResolvedForMSI(String msiAcc, ArrayList<ExperimentalPropertyValue<ExperimentalPropertyType>> propertyValues, String fileLocation) throws IOException {

        EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
        AnnotatorAccessor annotatorAccessor = new AnnotatorAccessor(em);
        File compare = new File(fileLocation + "/CompareResolvedWithDiscovered.txt");
        FileOutputStream outputStream = new FileOutputStream(compare, true);

        try {
            for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : propertyValues) {

                if (!FileUtils.readFileToString(compare).contains(pv.getTermText())) {

                    String discoveredAcc = null;
                    String resolvedAcc = null;

                    ExpPropValAnnotation expPropValAnnotation = getExpPropValAnnotation(pv, annotatorAccessor);
                    if (expPropValAnnotation != null) {
                        discoveredAcc = expPropValAnnotation.getOntoTermUri();
                    }


                    ResolvedOntoTermAnnotation resolvedOntTermAnn = getResolvedOntoTermAnnotation(pv, annotatorAccessor);
                    if (resolvedOntTermAnn != null) {
                        resolvedAcc = resolvedOntTermAnn.getOntoTermUri();
                    }

                    if (discoveredAcc != null && resolvedAcc != null && !discoveredAcc.equals(resolvedAcc)) {
                        outputStream.write(("================ " + msiAcc + " ================").getBytes());
                        outputStream.write("\n".getBytes());
                        outputStream.write(("Property Value: " + pv.getTermText() + " Property Type: " + pv.getType().getTermText()).getBytes());
                        outputStream.write("\n".getBytes());
                        outputStream.write(("Discovered: " + discoveredAcc + " from: " + expPropValAnnotation.getType()).getBytes());
                        outputStream.write("\n".getBytes());
                        outputStream.write(("Resolved:  " + resolvedAcc + " from: " + resolvedOntTermAnn.getType()).getBytes());
                        outputStream.write("\n".getBytes());
                        outputStream.write("\n".getBytes());

                    }
                }
            }
        } finally {
            if ( em.isOpen () ) em.close ();
            if (outputStream != null) outputStream.close();
        }
    }

    public void printAllOntologyAnnotationsForMSI(String msiAcc, ArrayList<ExperimentalPropertyValue<ExperimentalPropertyType>> propertyValues, String fileLocation) throws IOException {

        EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
        AnnotatorAccessor annotatorAccessor = new AnnotatorAccessor(em);
        File file = new File(fileLocation + "/Acc_" + msiAcc);
        FileOutputStream outputStream = new FileOutputStream(file);

        try {
            outputStream.write(msiAcc.getBytes());
            outputStream.write("\n".getBytes());

            for (ExperimentalPropertyValue<ExperimentalPropertyType> pv : propertyValues) {

                if (!FileUtils.readFileToString(file).contains(pv.getTermText())) {

                    String discoveredAcc = null;
                    String resolvedAcc = null;
                    outputStream.write("\n".getBytes());
                    outputStream.write(("Property Value: " + pv.getTermText() + " Property Type: " + pv.getType().getTermText()).getBytes());
                    outputStream.write("\n".getBytes());

                    ExpPropValAnnotation expPropValAnnotation = getExpPropValAnnotation(pv, annotatorAccessor);
                    if (expPropValAnnotation != null) {
                        outputStream.write(("Discovered:".getBytes()));
                        outputStream.write("\n".getBytes());
                        discoveredAcc = expPropValAnnotation.getOntoTermUri();
                        outputStream.write((discoveredAcc.getBytes()));
                        outputStream.write("\n".getBytes());
                    }

                    ResolvedOntoTermAnnotation resolvedOntoTermAnn = getResolvedOntoTermAnnotation(pv, annotatorAccessor);
                    if (resolvedOntoTermAnn != null) {
                        outputStream.write(("Resolved:".getBytes()));
                        outputStream.write("\n".getBytes());
                        resolvedAcc = resolvedOntoTermAnn.getOntoTermUri();
                        outputStream.write((resolvedAcc.getBytes()));
                        outputStream.write("\n".getBytes());
                    }
                }
            }
        }
        finally {
            if ( em.isOpen () ) em.close ();
            if (outputStream != null) outputStream.close();
        }
    }

    public void compareDiscoveredAndResolvedForMSI(PropertyValAnnotationService annService, String msiAcc, String fileLocation) throws IOException {
        EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
        try {
            AccessibleDAO<MSI> dao = new AccessibleDAO<>(MSI.class, em);
            MSI msi = dao.find(msiAcc);
            if (msi == null) throw new RuntimeException("Cannot find submission '" + msiAcc + "'");
            ArrayList<ExperimentalPropertyValue<ExperimentalPropertyType>> propertyValues = annService.getPropertyValuesOfMSI(msi);
            compareDiscoveredAndResolvedForMSI(msiAcc, propertyValues, fileLocation);
        }
        finally {
            if ( em.isOpen () ) em.close ();
        }
    }

    public void printAllOntologyAnnotationsForMSI(PropertyValAnnotationService annService, String msiAcc, String fileLocation) throws IOException {
        EntityManager em = Resources.getInstance ().getEntityManagerFactory ().createEntityManager ();
        try {
            AccessibleDAO<MSI> dao = new AccessibleDAO<>(MSI.class, em);
            MSI msi = dao.find(msiAcc);
            if (msi == null) throw new RuntimeException("Cannot find submission '" + msiAcc + "'");
            ArrayList<ExperimentalPropertyValue<ExperimentalPropertyType>> propertyValues = annService.getPropertyValuesOfMSI(msi);
            printAllOntologyAnnotationsForMSI(msiAcc, propertyValues, fileLocation);
        }
        finally {
            if ( em.isOpen () ) em.close ();
        }
    }

}
