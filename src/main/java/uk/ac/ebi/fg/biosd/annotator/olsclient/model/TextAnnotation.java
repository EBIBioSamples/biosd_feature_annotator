package uk.ac.ebi.fg.biosd.annotator.olsclient.model;

import org.apache.commons.lang3.ArrayUtils;
import uk.ac.ebi.bioportal.webservice.client.BioportalClient;


/**
 * <p>A Java representation of the JSON results coming from the annotator API.
 * @see {@link BioportalClient#getTextAnnotations(String, String...)}</p>
 * 
 * <p>TODO: we still haven't unclear what 'mappings', a field on the first level of the JSON result is for. This is 
 * ignored/unsupported at the moment.</p>
 * 
 * @author brandizi
 * <dl><dt>Date:</dt><dd>4 Aug 2015</dd>
 *
 */
public class TextAnnotation
{
	public static class Annotation
	{
		private int from, to;
		private String matchType;
		private String text;
		
		public int getFrom () {
			return from;
		}
		
		public void setFrom ( int from ) {
			this.from = from;
		}
		
		public int getTo () {
			return to;
		}
		
		public void setTo ( int to ) {
			this.to = to;
		}
		
		public String getMatchType () {
			return matchType;
		}
		
		public void setMatchType ( String matchType ) {
			this.matchType = matchType;
		}
		
		public String getText () {
			return text;
		}
		
		public void setText ( String text ) {
			this.text = text;
		}

		
		public Annotation ( int from, int to, String matchType, String text )
		{
			this.from = from;
			this.to = to;
			this.matchType = matchType;
			this.text = text;
		}
		
		@Override
		public String toString ()
		{
			return String.format ( 
				"%s { text: '%s' from: %d, to: %d, matchType: '%s' }", 
				this.getClass ().getSimpleName (), this.getText (), this.getFrom (), this.getTo (), 
				this.getMatchType ()
			);
		}
		
	}

	
	
	public static class HierarchyEntry
	{
		private ClassRef classRef;
		private int distance;
		
		public ClassRef getClassRef () {
			return classRef;
		}

		public void setClassRef ( ClassRef classRef ) {
			this.classRef = classRef;
		}

		public int getDistance () {
			return distance;
		}

		public void setDistance ( int distance ) {
			this.distance = distance;
		}


		public HierarchyEntry (ClassRef classRef, int distance )
		{
			this.classRef = classRef;
			this.distance = distance;
		}
		
		@Override
		public String toString ()
		{
			return String.format ( 
				"%s { classRef: %s, distance: %d }", 
				this.getClass ().getSimpleName (), this.getClassRef (), this.getDistance ()
			);
		}
	}

	
	private ClassRef annotatedClass;
	private HierarchyEntry[] hierarchy;
	private Annotation[] annotations;
	
	public ClassRef getAnnotatedClass () {
		return annotatedClass;
	}
	
	public void setAnnotatedClass ( ClassRef annotatedClass ) {
		this.annotatedClass = annotatedClass;
	}
	
	public HierarchyEntry[] getHierarchy () {
		return hierarchy;
	}
	
	public void setHierarchy ( HierarchyEntry[] hierarchy ) {
		this.hierarchy = hierarchy;
	}
	
	public Annotation[] getAnnotations () {
		return annotations;
	}
	
	public void setAnnotations ( Annotation[] annotations ) {
		this.annotations = annotations;
	}

	public TextAnnotation ( ClassRef annotatedClass )
	{
		this.annotatedClass = annotatedClass;
	}

	@Override
	public String toString ()
	{
		return String.format ( 
			"%s { annotatedClass: %s, annotations: %s, hierarchy: %s }", 
			this.getClass ().getSimpleName (), this.getAnnotatedClass (), 
			ArrayUtils.toString ( this.getAnnotations () ),
			ArrayUtils.toString ( this.getHierarchy () )
		);
	}
}
