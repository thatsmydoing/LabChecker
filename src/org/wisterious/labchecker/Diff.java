package org.wisterious.labchecker;

import difflib.*;
import java.util.*;
import java.io.*;
import org.apache.commons.io.FileUtils;

public class Diff {  
  public static List<DiffRow> diff(File a, File b) throws IOException {
    return diff((List<String>)FileUtils.readLines(a), (List<String>)FileUtils.readLines(b));
  }
  
  public static List<DiffRow> diff(List<String> a, List<String> b) {
    DiffRowGenerator generator = new DiffRowGenerator.Builder().showInlineDiffs(true).ignoreWhiteSpaces(true).columnWidth(100).build();
    return generator.generateDiffRows(a, b);
  }
}