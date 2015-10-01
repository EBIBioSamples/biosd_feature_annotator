package uk.ac.ebi.fg.biosd.annotator.resources;

import org.apache.commons.lang3.ArrayUtils;

import uk.ac.ebi.fg.biosd.model.resources.BioSdResources;
import uk.ac.ebi.fg.core_model.resources.Resources;

/**
 * The customisation of {@link Resources} needed for the biosd_model package.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>2 Sep 2015</dd>
 *
 */
public class FeatureAnnotatorResources extends BioSdResources
{
	/**
	 * Model tables provided by the annotator are inside the same DB used by BioSD, so we prefer to prefix them.
	 */
	public static final String TABLE_PREFIX = "fann_";
	
	/**
	 * Overrides biosd_model.
	 */
	@Override
	public int getPriority () {
		return super.getPriority () + 10;
	}

	/**
	 * We have a couple of classes where to store annotations.
	 * 
	 */
	@Override
	public String[] getPackagesToScan ()
	{
		return ArrayUtils.add ( super.getPackagesToScan (), 0, "uk.ac.ebi.fg.biosd.annotator.model.**" );
	}

}
