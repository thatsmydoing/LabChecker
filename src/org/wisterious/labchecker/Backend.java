package org.wisterious.labchecker;

import java.io.*;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class Backend {
  static final FilenameFilter archiveFilter = new FilenameFilter() {
    public boolean accept(File dir, String name) {
      return name.endsWith(".zip") || name.endsWith(".rar");
    }
  };
  
  static final FilenameFilter classFilter = new FilenameFilter() {
    public boolean accept(File dir, String name) {
      return name.endsWith(".class");
    }
  };

  static final FileFilter submissionFilter = new FileFilter() {
      public boolean accept(File f) {
          return f.isDirectory() && archiveFilter.accept(null, f.getName());
      }
  };

  public static void extractAll(File directory, File outputDir) {
    File[] archives = directory.listFiles(archiveFilter);
    for(File archive : archives) {
      File newDir = new File(outputDir, archive.getName());
      newDir.mkdir();
      Extract.extract(archive, newDir);
    }
  }
  
  public static File[] getSubmissions(File directory) {
    return directory.listFiles(submissionFilter);
  }
  
  public static File findSourceDir(File directory) {
    LinkedList<File> queue = new LinkedList<File>();
    queue.add(directory);
    while(!queue.isEmpty()) {
      File dir = queue.remove();
      for(File f : dir.listFiles()) {
        if(f.isDirectory()) {
          queue.add(f);
        }
        else if(f.getName().endsWith(".java")) {
          return dir;
        }
      }
    }
    return null;
  }
  
  public static ChildProcess compile(final File directory) throws IOException {
    String[] params = new String[2];
    params[0] = "javac";
    params[1] = "*.java";
    
    Runtime rt = Runtime.getRuntime();
    final Process p = rt.exec(params, null, directory);
    
    
    ChildProcess cp = new ChildProcess(p) {
      public void runBody() {
        try {
          String errors = IOUtils.toString(p.getErrorStream());
          FileWriter fw = new FileWriter(new File(directory, "compiler-output.txt"));
          fw.write(errors);
          fw.close();
        }
        catch (Exception e) {
        
        }
      }
    };
    return cp;
  }
  
  public static List<File> getMainFiles(File directory) {
    ArrayList<File> mainFiles = new ArrayList<File>();
    for(File f : directory.listFiles(classFilter)) {
      try {
        String contents = FileUtils.readFileToString(f);
        if(contents.contains("main")) {
          mainFiles.add(f);
        }
      }
      catch (Exception e) {}
    }
    return mainFiles;
  }

  public static void runInteractive(File directory, File mainFile, File inputFile) throws Exception {
      String os = System.getProperty("os.name").toLowerCase();
      if(os.contains("win")) {
          String terminalCommand = String.format("cmd /C \"java %s %s && pause\"",
                  mainFile.getName().replace(".class", ""),
                  inputFile == null ? "" : "< " + inputFile.getName());
          Runtime rt = Runtime.getRuntime();
          Process p = rt.exec(terminalCommand, null, directory);
      }
      throw new Exception("No terminal command defined");
  }


  public static ChildProcess run(final File directory, File mainFile, File inputFile) throws IOException {
    String[] params = new String[2];
    params[0] = "java";
    params[1] = mainFile.getName().replace(".class", "");
    
    Runtime rt = Runtime.getRuntime();
    final Process p = rt.exec(params, null, directory);
    
    if(inputFile != null) {
      BufferedWriter wr = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
      wr.append(FileUtils.readFileToString(inputFile));
      wr.close();
    }
        
    ChildProcess cp = new ChildProcess(p) {
      public void runBody() {
        try {
          String errors = IOUtils.toString(p.getErrorStream());
          String output = IOUtils.toString(p.getInputStream());
          FileWriter fw = new FileWriter(new File(directory, "run-output.txt"));
          fw.write(output);
          fw.close();
          
          fw = new FileWriter(new File(directory, "run-errors.txt"));
          fw.write(errors);
          fw.close();
        }
        catch (Exception e) {}
      }
    };
    return cp;
  }

  public static void openEditor(File directory) throws IOException {
    File[] files = directory.listFiles(new FileFilter() {
        public boolean accept(File pathname) {
            if(!pathname.isFile()) return false;
            if(pathname.getName().endsWith(".java")) return true;
            if(pathname.getName().endsWith(".txt")) return true;
            if(pathname.getName().endsWith(".in")) return true;
            if(pathname.getName().endsWith(".out")) return true;
            return false;
        }
    });

    StringBuilder command = new StringBuilder();
    command.append("java -jar jedit.jar -settings=settings");
    
    for(File f : files) {
        command.append(" \"");
        command.append(f.getAbsolutePath());
        command.append("\"");
    }

    Runtime rt = Runtime.getRuntime();
    final Process p = rt.exec(command.toString(), null, new File("jedit"));
  }

  public static void openFolder(File directory) throws Exception {
    String os = System.getProperty("os.name").toLowerCase();
    if(os.contains("win")) {
        Runtime rt = Runtime.getRuntime();
        rt.exec("explorer.exe", null, directory);
    }
    if(os.contains("nix") || os.contains("nux")) {
        Runtime rt = Runtime.getRuntime();
        rt.exec("nautilus .", null, directory);
    }
    else {
        throw new Exception("No file browser command defined");
    }
  }
}
