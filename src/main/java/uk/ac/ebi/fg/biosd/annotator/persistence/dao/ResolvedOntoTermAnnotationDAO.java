package uk.ac.ebi.fg.biosd.annotator.persistence.dao;

import javax.persistence.EntityManager;

import org.hibernate.Session;

import uk.ac.ebi.fg.biosd.annotator.model.ResolvedOntoTermAnnotation;
import uk.ac.ebi.fg.core_model.persistence.dao.hibernate.toplevel.AbstractDAO;
import uk.ac.ebi.fg.core_model.terms.OntologyEntry;

/**
 * TODO: comment me!
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>1 Sep 2015</dd>
 *
 */
public class ResolvedOntoTermAnnotationDAO extends AbstractDAO<ResolvedOntoTermAnnotation>
{

	public ResolvedOntoTermAnnotationDAO ( EntityManager entityManager )
	{
		super ( ResolvedOntoTermAnnotation.class, entityManager );
	}
	
	public ResolvedOntoTermAnnotation findBySourceText ( String sourceText, boolean isReadOnly )
	{
		if ( sourceText == null ) return null;
		
		Session session = (Session) this.getEntityManager ().getDelegate ();
		session.setDefaultReadOnly ( isReadOnly );
		return (ResolvedOntoTermAnnotation) session.get ( ResolvedOntoTermAnnotation.class, sourceText );
	}
	
	public ResolvedOntoTermAnnotation findBySourceText ( String sourceText )
	{
		return findBySourceText ( sourceText, false );
	}

	
	public ResolvedOntoTermAnnotation findByOntoTerm ( OntologyEntry oe, boolean isReadOnly )
	{
		return findBySourceText ( ResolvedOntoTermAnnotation.getOntoEntryText ( oe ), isReadOnly );
	}

	public ResolvedOntoTermAnnotation findByOntoTerm ( OntologyEntry oe )
	{
		return findByOntoTerm ( oe, false );
	}
	
}
