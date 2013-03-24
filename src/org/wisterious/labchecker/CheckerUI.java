/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * CheckerUI.java
 *
 * Created on 07 3, 10, 10:12:09 PM
 */

package org.wisterious.labchecker;

import difflib.DiffRow;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author thomas
 */
public class CheckerUI extends javax.swing.JFrame {
    int currIndex;
    File[] submissions;
    File inputFile;
    File outputFile;

    File localInputFile;
    File localOutputFile;
    
    File sourceFolder;
    ChildProcess compileProcess;
    ChildProcess runProcess;

    /** Creates new form CheckerUI */
    public CheckerUI(File[] submissions, File inputFile, File outputFile) {
        this.currIndex = 0;
        this.submissions = submissions;
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.sourceFolder = null;
        compileProcess = null;
        runProcess = null;
        initComponents();
        setupEditorPanes();
        loadSubmission(0);
    }

    private void setupEditorPanes() {
        actualOutputScrollPane.setVerticalScrollBar(expectedOutputScrollPane.getVerticalScrollBar());
        StyleSheet s = new StyleSheet();
        s.addRule("editNewInline { background: red }");
        s.addRule("editOldInline { background: yellow }");
        HTMLEditorKit editorKit = new HTMLEditorKit();
        editorKit.setStyleSheet(s);

        actualOutputPane.setEditorKit(editorKit);
        expectedOutputPane.setEditorKit(editorKit);
    }

    private void loadSubmission(int index) {
        currIndex = index;
        this.setTitle(submissions[currIndex].getName());
        numberButton.setText(1+currIndex+"");
        compilerOutputPane.setText("");
        runOutputPane.setText("");
        runErrorsPane.setText("");
        expectedOutputPane.setText("");
        actualOutputPane.setText("");
        compileButton.setEnabled(true);
        editSourcesButton.setEnabled(true);
        runButton.setEnabled(false);
        runInteractiveButton.setEnabled(false);
        sourceFolder = Backend.findSourceDir(submissions[currIndex]);

        if(sourceFolder == null) {
            JOptionPane.showMessageDialog(this, "Could not find the source folder");
            compileButton.setEnabled(false);
            editSourcesButton.setEnabled(false);
        }

        localInputFile = null;
        localOutputFile = null;

        try {
            if(inputFile != null) {
                localInputFile = new File(sourceFolder, inputFile.getName());
                FileUtils.copyFile(inputFile, localInputFile);
            }
            if(outputFile != null) {
                localOutputFile = new File(sourceFolder, outputFile.getName());
                FileUtils.copyFile(outputFile, localOutputFile);
            }
            

            if(autocompileCheckBox.isSelected()) {
                startCompile();
            }
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not copy input/output files");
        }
    }

    private void startCompile() {
        if(sourceFolder == null) {
            JOptionPane.showMessageDialog(this, "There are no source files");
            return;
        }
        
        try {
            compileProcess = Backend.compile(sourceFolder);
            compileProcess.addListener(new ChildProcessListener() {
                public void processDone(int returnValue) {
                    compileFinished(returnValue);
                }
            });
            compileProcess.start();
            compileButton.setText("Stop Compile");
            runButton.setEnabled(false);
            runInteractiveButton.setEnabled(false);
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not run compiler");
        }
    }

    private void endCompile() {
        compileProcess.kill();
    }

    private void compileFinished(int returnValue) {
        compileProcess = null;
        compileButton.setText("Compile");
        if(returnValue == 0) {
            compilerOutputPane.setText("<compile successful>");
            runButton.setEnabled(true);
            runInteractiveButton.setEnabled(true);
            listMainFiles();
            if(autorunCheckBox.isSelected()) {
                startRun();
            }
        }
        else {
            try {
                String compileOutput = FileUtils.readFileToString(new File(sourceFolder, "compiler-output.txt"));
                if(compileOutput.trim().equals(""))  {
                    compileOutput = "<no output>";
                }
                compilerOutputPane.setText(compileOutput);
            }
            catch (Exception e) {
                compilerOutputPane.setText("<no output>");
            }
        }
        tabPane.setSelectedIndex(0);
    }

    private void listMainFiles() {
        List<File> mainFiles = Backend.getMainFiles(sourceFolder);
        mainFileComboBox.removeAllItems();
        if(mainFiles.size() > 0) {
            for(File mainFile : mainFiles) {
                mainFileComboBox.addItem(mainFile);
            }
        }
        else {
            mainFileComboBox.addItem(null);
        }
        mainFileComboBox.setSelectedIndex(0);
    }

    private void startRun() {
        if(mainFileComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "There are no main files");
            return;
        }
        try {
            File mainFile = (File) mainFileComboBox.getSelectedItem();
            runProcess = Backend.run(sourceFolder, mainFile, fileInputCheckBox.isSelected() ? null : localInputFile);
            runProcess.addListener(new ChildProcessListener() {
                public void processDone(int returnValue) {
                    runFinished(returnValue);
                }
            });
            runProcess.start();
            compileButton.setEnabled(false);
            runButton.setText("Stop Running");
            runInteractiveButton.setEnabled(false);
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not run java");
        }
    }

    private void endRun() {
        runProcess.kill();
    }

    private void runFinished(int returnValue) {
        runProcess = null;
        runButton.setText("Run");
        compileButton.setEnabled(true);
        runInteractiveButton.setEnabled(true);

        try {
            String runOutput = FileUtils.readFileToString(new File(sourceFolder, "run-output.txt"));
            if(runOutput.trim().equals("")) {
                runOutput = "<no output>";
            }
            runOutputPane.setText(runOutput);
        }
        catch (Exception e) {
            runOutputPane.setText("<no output>");
        }

        try {
            String runErrors = FileUtils.readFileToString(new File(sourceFolder, "run-errors.txt"));
            if(runErrors.trim().equals("")) {
                runErrors = "<no errors>";
            }
            runErrorsPane.setText(runErrors);
        }
        catch (Exception e) {
            runErrorsPane.setText("<no errors>");
        }

        if(returnValue == 0) {
            tabPane.setSelectedIndex(1);
        }
        else {
            tabPane.setSelectedIndex(2);
        }
        processDiff();
    }

    private void processDiff() {
        expectedOutputPane.setText("<html><body>Processing...</body></html>");
        actualOutputPane.setText("<html><body>Processing...</body></html>");
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    List<DiffRow> diffs = Diff.diff(localOutputFile, new File(sourceFolder, "run-output.txt"));
                    StringBuilder actualOutput = new StringBuilder();
                    StringBuilder expectedOutput = new StringBuilder();
                    expectedOutput.append("<html><body>");
                    actualOutput.append("<html><body>");
                    for(DiffRow diff : diffs) {
                        expectedOutput.append(diff.getOldLine());
                        expectedOutput.append("<br>");
                        actualOutput.append(diff.getNewLine());
                        actualOutput.append("<br>");
                    }
                    expectedOutput.append("</body></html>");
                    actualOutput.append("</body></html>");
                    expectedOutputPane.setText(expectedOutput.toString());
                    actualOutputPane.setText(actualOutput.toString());
                }
                catch (Exception e) {
                    expectedOutputPane.setText("<html><body>Processing failed D:</body></html>");
                    actualOutputPane.setText("<html><body>Processing failed D:</body></html>");
                }
            }
        };
        t.start();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabPane = new javax.swing.JTabbedPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        compilerOutputPane = new javax.swing.JTextPane();
        jScrollPane2 = new javax.swing.JScrollPane();
        runOutputPane = new javax.swing.JTextPane();
        jScrollPane3 = new javax.swing.JScrollPane();
        runErrorsPane = new javax.swing.JTextPane();
        jPanel1 = new javax.swing.JPanel();
        expectedOutputScrollPane = new javax.swing.JScrollPane();
        expectedOutputPane = new javax.swing.JEditorPane();
        actualOutputScrollPane = new javax.swing.JScrollPane();
        actualOutputPane = new javax.swing.JEditorPane();
        prevButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        numberButton = new javax.swing.JButton();
        compileButton = new javax.swing.JButton();
        runButton = new javax.swing.JButton();
        runInteractiveButton = new javax.swing.JButton();
        editSourcesButton = new javax.swing.JButton();
        autorunCheckBox = new javax.swing.JCheckBox();
        openFolderButton = new javax.swing.JButton();
        fileInputCheckBox = new javax.swing.JCheckBox();
        autocompileCheckBox = new javax.swing.JCheckBox();
        mainFileComboBox = new javax.swing.JComboBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        jScrollPane1.setViewportView(compilerOutputPane);

        tabPane.addTab("Compiler Output", jScrollPane1);

        jScrollPane2.setViewportView(runOutputPane);

        tabPane.addTab("Run Output", jScrollPane2);

        jScrollPane3.setViewportView(runErrorsPane);

        tabPane.addTab("Run Errors", jScrollPane3);

        jPanel1.setLayout(new java.awt.GridLayout(1, 2));

        expectedOutputScrollPane.setViewportView(expectedOutputPane);

        jPanel1.add(expectedOutputScrollPane);

        actualOutputScrollPane.setViewportView(actualOutputPane);

        jPanel1.add(actualOutputScrollPane);

        tabPane.addTab("Output Comparison", jPanel1);

        prevButton.setText("<");
        prevButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevButtonActionPerformed(evt);
            }
        });

        exitButton.setText("Exit");
        exitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitButtonActionPerformed(evt);
            }
        });

        nextButton.setText(">");
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        numberButton.setText(" ");
        numberButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                numberButtonActionPerformed(evt);
            }
        });

        compileButton.setText("Compile");
        compileButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compileButtonActionPerformed(evt);
            }
        });

        runButton.setText("Run");
        runButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runButtonActionPerformed(evt);
            }
        });

        runInteractiveButton.setText("Run Interactive");
        runInteractiveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runInteractiveButtonActionPerformed(evt);
            }
        });

        editSourcesButton.setText("Edit Sources");
        editSourcesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editSourcesButtonActionPerformed(evt);
            }
        });

        autorunCheckBox.setText("Auto-run");

        openFolderButton.setText("Open Folder");
        openFolderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openFolderButtonActionPerformed(evt);
            }
        });

        fileInputCheckBox.setText("File Input");

        autocompileCheckBox.setText("Auto-compile");

        mainFileComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "no main file" }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(autorunCheckBox, 0, 0, Short.MAX_VALUE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(compileButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(mainFileComboBox, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(runButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(runInteractiveButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(editSourcesButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(openFolderButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(exitButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                            .addComponent(prevButton)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(numberButton, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(nextButton)))
                    .addComponent(autocompileCheckBox)
                    .addComponent(fileInputCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tabPane, javax.swing.GroupLayout.DEFAULT_SIZE, 476, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tabPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 381, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(compileButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(mainFileComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(runButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(runInteractiveButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(editSourcesButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(openFolderButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 31, Short.MAX_VALUE)
                        .addComponent(fileInputCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(autocompileCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(autorunCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(prevButton)
                            .addComponent(numberButton)
                            .addComponent(nextButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exitButton)))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void prevButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevButtonActionPerformed
        if(runProcess != null || compileProcess != null) return;
        if(currIndex > 0) {
            currIndex--;
            loadSubmission(currIndex);
        }
    }//GEN-LAST:event_prevButtonActionPerformed

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        if(runProcess != null || compileProcess != null) return;
        if(currIndex < submissions.length - 1) {
            currIndex++;
            loadSubmission(currIndex);
        }
    }//GEN-LAST:event_nextButtonActionPerformed

    private void compileButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compileButtonActionPerformed
        if(compileProcess == null) {
            startCompile();
        }
        else {
            endCompile();
        }
    }//GEN-LAST:event_compileButtonActionPerformed

    private void runButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runButtonActionPerformed
        if(runProcess == null) {
            startRun();
        }
        else {
            endRun();
        }
    }//GEN-LAST:event_runButtonActionPerformed

    private void runInteractiveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runInteractiveButtonActionPerformed
        try {
            Backend.runInteractive(sourceFolder, (File)mainFileComboBox.getSelectedItem(), inputFile);
        }
        catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not open terminal");
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }//GEN-LAST:event_runInteractiveButtonActionPerformed

    private void editSourcesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editSourcesButtonActionPerformed
        try {
            Backend.openEditor(sourceFolder);
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not open editor");
        }
    }//GEN-LAST:event_editSourcesButtonActionPerformed

    private void openFolderButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openFolderButtonActionPerformed
        try {
            if(sourceFolder == null) {
                Backend.openFolder(submissions[currIndex]);
            } else {
                Backend.openFolder(sourceFolder);
            }
        }
        catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not open folder");
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }//GEN-LAST:event_openFolderButtonActionPerformed

    private void exitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitButtonActionPerformed
        this.dispose();
    }//GEN-LAST:event_exitButtonActionPerformed

    private void numberButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_numberButtonActionPerformed
        if(runProcess != null || compileProcess != null) return;
        String input = JOptionPane.showInputDialog(this, "Jump to submission number", currIndex);
        if(input.matches("\\d+")) {
            int submission = Integer.parseInt(input);
            if(submission >= 0 && submission < submissions.length && submission != currIndex) {
                loadSubmission(submission);
            }
        }
    }//GEN-LAST:event_numberButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JEditorPane actualOutputPane;
    private javax.swing.JScrollPane actualOutputScrollPane;
    private javax.swing.JCheckBox autocompileCheckBox;
    private javax.swing.JCheckBox autorunCheckBox;
    private javax.swing.JButton compileButton;
    private javax.swing.JTextPane compilerOutputPane;
    private javax.swing.JButton editSourcesButton;
    private javax.swing.JButton exitButton;
    private javax.swing.JEditorPane expectedOutputPane;
    private javax.swing.JScrollPane expectedOutputScrollPane;
    private javax.swing.JCheckBox fileInputCheckBox;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JComboBox mainFileComboBox;
    private javax.swing.JButton nextButton;
    private javax.swing.JButton numberButton;
    private javax.swing.JButton openFolderButton;
    private javax.swing.JButton prevButton;
    private javax.swing.JButton runButton;
    private javax.swing.JTextPane runErrorsPane;
    private javax.swing.JButton runInteractiveButton;
    private javax.swing.JTextPane runOutputPane;
    private javax.swing.JTabbedPane tabPane;
    // End of variables declaration//GEN-END:variables

}
