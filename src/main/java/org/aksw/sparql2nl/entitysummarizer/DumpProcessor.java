/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.util.List;

/**
 *
 * @author ngonga
 */
public interface DumpProcessor {
    List<LogEntry> processDump(String file);
}