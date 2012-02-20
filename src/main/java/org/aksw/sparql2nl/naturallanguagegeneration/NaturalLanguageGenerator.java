package org.aksw.sparql2nl.naturallanguagegeneration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.aksw.sparql2nl.corpuscreation.Kreator;
import org.aksw.sparql2nl.queryprocessing.Query;
import org.aksw.sparql2nl.queryprocessing.Similarity;
import org.aksw.sparql2nl.queryprocessing.Similarity.SimilarityMeasure;
import org.dllearner.algorithm.tbsl.sparql.Slot;
import org.dllearner.algorithm.tbsl.sparql.Template;
import org.dllearner.algorithm.tbsl.templator.Templator;

import simpack.measure.graph.GraphIsomorphism;
import simpack.measure.graph.SubgraphIsomorphism;

import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

import edu.stanford.nlp.util.StringUtils;


public class NaturalLanguageGenerator {

	private Query query;
	private Template template;
	private Map<String, String> labels;
	private String question;
	
	private Map<String,String> templateToQueryVariables = new HashMap<String,String>();
	private Map<String,String> queryToTemplateVariables = new HashMap<String,String>();

	public NaturalLanguageGenerator(Query query, Template template, String question) {
		
		this.query =  query;
		this.template = template;
		this.question = question;
		this.labels = new HashMap<String,String>();
	}
	
	public String generateNaturalLanguageFromSparqlQuery() {
		
		this.createVariableMappings();
		this.populateLabels();
		
//		System.out.println("QueryToVariables: " + queryToTemplateVariables);
//		System.out.println("Labels: " + labels);
//		System.out.println("Question: " + question);
//		System.out.println("Template: " + template);
		
		for ( Slot slot : this.template.getSlots() ) {
			
			String uri = templateToQueryVariables.get("?" + slot.getAnchor());
			if(uri == null) continue;
			if ( !uri.startsWith("?") ) {
				
				String words = StringUtils.join(slot.getWords(), " ");
				String replacement = this.labels.get(uri);
				
//				System.out.println("URI: " + uri);
//				System.out.println("Slot-Words: " + words);
//				System.out.println("Replacement: " + replacement);
				
				this.question = this.question.replace(words, !replacement.equals("N/A") ? replacement : words + "_" + replacement);
			}
		}
		return this.question;
	}
	
	
	private void createVariableMappings() {

		Query query1 = this.query;
		Query query2 = new Query(this.template.getQuery().toString());
		
		GraphIsomorphism si = new GraphIsomorphism(query1.getGraphRepresentation(), query2.getGraphRepresentation());
		si.calculate();
		
		// two queries are isomorph
		if ( Similarity.getSimilarity(query1, query2, SimilarityMeasure.GRAPH_ISOMORPHY) > 0 ) {
			
			if ( !si.getCliqueList().isEmpty() ) {
				
//				System.out.println(Similarity.getSimilarity(query1, query2, SimilarityMeasure.GRAPH_ISOMORPHY));
//				System.out.println(query1.getOriginalQuery());
//				System.out.println(query2.getOriginalQuery());
//				System.out.println("QL: " + si.getCliqueList());
				
				for (String pairs : new ArrayList<String>(si.getCliqueList()).get(0).split(",")) {
					
					String[] part = pairs.split(":");
					
					String query1Variable = part[0].trim();
					String query2Variable = part[1].trim();
					
					templateToQueryVariables.put(query2.getVar2NonVar().get(query2Variable), query1.getVar2NonVar().get(query1Variable));
					queryToTemplateVariables.put(query1.getVar2NonVar().get(query1Variable), query2.getVar2NonVar().get(query2Variable));
				}
			}
		}
		else {
			
			throw new IllegalArgumentException("Queries are not isomorph");
		}
	}

	/**
	 * create a list of all labels for non-variables in the query
	 */
	private void populateLabels() {

		for (String variable : this.queryToTemplateVariables.keySet() ) {
			
			if ( variable != null ) {
				
				// we only want to query for resources not variables like ?x
				if (!variable.startsWith("?") && !variable.startsWith("$")) {
					
					this.labels.put(variable, this.queryDBpediaForLabel(variable));
				}
			}
			else { 
				System.out.println("Variable null");
			}
		}
	}

	/**
	 * Query dbpedia to get english labels
	 * 
	 * @param variable
	 * @return
	 */
	private String queryDBpediaForLabel(String variable) {

		String query = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> SELECT ?label { " + variable + " rdfs:label ?label . Filter(lang(?label) = 'en') } ";
		QueryEngineHTTP qexec = new QueryEngineHTTP("http://live.dbpedia.org/sparql/", query);
		qexec.addDefaultGraph("http://dbpedia.org");
		ResultSet results = qexec.execSelect();
		
		while ( results.hasNext() ) {
			
			String label = results.next().get("?label").toString();
			return label.substring(0, label.indexOf("@"));
		}
		return "N/A";
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		String queryString = "Who founded Microsoft?";
		
		Templator temp = new Templator(false); // Templator without WordNet and with VERBOSE=false
		temp.setGrammarFiles(Kreator.LEXICON);
		Template template = new ArrayList<Template>(temp.buildTemplates(queryString)).get(0);

		Query query = new Query("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> SELECT ?x WHERE { ?x <http://dbpedia.org/ontology/foundedBy> <http://dbpedia.org/resource/Apple_Inc.> . }");
		
//		System.out.println("Template-Query: " + template);
//		System.out.println("Translate-Query: " + query.getOriginalQuery());
		
		NaturalLanguageGenerator nlg = new NaturalLanguageGenerator(query, template, queryString);
		System.out.println(nlg.generateNaturalLanguageFromSparqlQuery());
	}

}