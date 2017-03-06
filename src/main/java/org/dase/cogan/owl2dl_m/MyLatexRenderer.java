package org.dase.cogan.owl2dl_m;

import static org.semanticweb.owlapi.util.OWLAPIPreconditions.verifyNotNull;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.semanticweb.owlapi.io.AbstractOWLRenderer;
import org.semanticweb.owlapi.io.OWLRendererException;
import org.semanticweb.owlapi.latex.renderer.LatexRendererIOException;
import org.semanticweb.owlapi.latex.renderer.LatexWriter;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLRuntimeException;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.util.OWLEntityComparator;
import org.semanticweb.owlapi.util.ShortFormProvider;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;

public class MyLatexRenderer extends AbstractOWLRenderer
{

	private final ShortFormProvider		shortFormProvider	= new SimpleShortFormProvider();
	private final OWLEntityComparator	entityComparator	= new OWLEntityComparator(shortFormProvider);

	private void writeEntitySection(OWLEntity entity, LatexWriter w)
	{
		w.write("\\subsubsection*{");
		w.write(escapeName(shortFormProvider.getShortForm(entity)));
		w.write("}\n\n");
	}

	private static String escapeName(String name)
	{
		return name.replace("_", "\\_").replace("#", "\\#");
	}

	@Override
	public void render(OWLOntology o, PrintWriter _w) throws OWLRendererException
	{
		try
		{
			LatexWriter w = new LatexWriter(_w);

			// Begin preamble
			w.write("\\documentclass{article}\n");
			// For those pesky multiline equations. Breqn currently breaks with
			// certain amsmath commands.
			w.write("\\usepackage{amsmath}\n");
			w.write("\\usepackage{breqn}\n");
			w.write("\\parskip 0pt\n");
			w.write("\\parindent 0pt\n");
			w.write("\\oddsidemargin 0cm\n");
			w.write("\\textwidth 19cm\n");
			w.write("\\begin{document}\n\n");

			MyLatexObjectVisitor renderer = new MyLatexObjectVisitor(w, o.getOWLOntologyManager().getOWLDataFactory());
			Collection<OWLClass> clses = sortEntities(o.classesInSignature());

			// Write Classes
			if(!clses.isEmpty())
			{
				w.write("\\subsection*{Classes}\n\n");
			}
			
			for(OWLClass cls : clses)
			{
				writeEntity(w, renderer, cls, sortAxioms(o.axioms(cls)));
			}

			w.write("\\section*{Object properties}");
			sortEntities(o.objectPropertiesInSignature()).forEach(p -> {
				writeEntity(w, renderer, p, sortAxioms(o.axioms(p)));
			});

			w.write("\\section*{Data properties}");
			o.dataPropertiesInSignature().sorted(entityComparator)
			        .forEach(prop -> writeEntity(w, renderer, prop, sortAxioms(o.axioms(prop))));
			w.write("\\section*{Individuals}");
			o.individualsInSignature().sorted(entityComparator)
			        .forEach(i -> writeEntity(w, renderer, i, sortAxioms(o.axioms(i))));

			 w.write("\\section*{Datatypes}");
			 o.datatypesInSignature().sorted(entityComparator)
			 .forEach(type -> writeEntity(w, renderer, type,
			 sortAxioms(o.axioms(type, Imports.EXCLUDED))));

			w.write("\\end{document}\n");
			w.flush();
		}
		catch(OWLRuntimeException e)
		{
			throw new LatexRendererIOException(e);
		}
	}

	protected void writeEntity(LatexWriter w, MyLatexObjectVisitor renderer, OWLEntity cls,
	        Collection<? extends OWLAxiom> axioms)
	{
		writeEntitySection(cls, w);
		for(OWLAxiom ax : axioms)
		{
			renderer.setSubject(cls);

			// Enter Mathmode
			w.write("$");
			// Render
			ax.accept(renderer);
			// Exit Mathmode
			w.write("$");
			
			w.write("\n\n");
		}
	}

	private <T extends OWLEntity> Collection<T> sortEntities(Stream<T> entities)
	{
		return asList(entities.sorted(entityComparator));
	}

	private static Collection<? extends OWLAxiom> sortAxioms(Stream<? extends OWLAxiom> axioms)
	{
		return asList(axioms.sorted(new OWLAxiomComparator()));
	}

	@SuppressWarnings("serial")
	private static class OWLAxiomComparator implements Comparator<OWLAxiom>, Serializable
	{

		OWLAxiomComparator()
		{
		}

		@Override
		public int compare(@Nullable OWLAxiom o1, @Nullable OWLAxiom o2)
		{
			int index1 = verifyNotNull(o1).getAxiomType().getIndex();
			int index2 = verifyNotNull(o2).getAxiomType().getIndex();
			return index1 - index2;
		}
	}
}