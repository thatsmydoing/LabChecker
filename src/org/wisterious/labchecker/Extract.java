/*
 * Almost completely copied from: http://sevenzipjbind.sourceforge.net/snippets/ExtractItemsStandardCallback.java
 */

package org.wisterious.labchecker;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.File;
import java.io.FileOutputStream;

import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.ISevenZipInArchive;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;

public class Extract {
    public static class MyExtractCallback implements IArchiveExtractCallback {
        private int index;
        private boolean skipExtraction;
        private ISevenZipInArchive inArchive;
        private File path;

        public MyExtractCallback(ISevenZipInArchive inArchive, File path) {
            this.inArchive = inArchive;
            this.path = path;
        }

        public ISequentialOutStream getStream(int index, 
                ExtractAskMode extractAskMode) throws SevenZipException {
            this.index = index;
            skipExtraction = (Boolean) inArchive
                    .getProperty(index, PropID.IS_FOLDER);
            if (skipExtraction) {
                return null;
            }            
            final String internalPath = inArchive.getStringProperty(this.index, PropID.PATH);
            return new ISequentialOutStream() {
              FileOutputStream fos = null;
              public int write(byte[] data) throws SevenZipException {
                if(fos == null) {
                  try {
                    File f = new File(path, internalPath);
                    f.getParentFile().mkdirs();
                    f.createNewFile();
                    fos = new FileOutputStream(f);
                  }
                  catch (IOException e) {
                    throw new SevenZipException(e.getMessage());
                  }
                }
                try {
                  fos.write(data);
                }
                catch (IOException e) {
                  throw new SevenZipException(e.getMessage());
                }
                return data.length; // Return amount of proceed data
              }
            };
        }

        public void prepareOperation(ExtractAskMode extractAskMode) 
                throws SevenZipException {
        }

        public void setOperationResult(ExtractOperationResult 
                extractOperationResult) throws SevenZipException {
            if (skipExtraction) {
                return;
            }
            if (extractOperationResult != ExtractOperationResult.OK) {
                System.err.println("Extraction error");
            }
        }

        public void setCompleted(long completeValue) throws SevenZipException {
        }

        public void setTotal(long total) throws SevenZipException {
        }

    }

    public static void extract(File file, File path) {
        RandomAccessFile randomAccessFile = null;
        ISevenZipInArchive inArchive = null;
        try {
            randomAccessFile = new RandomAccessFile(file, "r");
            inArchive = SevenZip.openInArchive(null, // autodetect archive type
                    new RandomAccessFileInStream(randomAccessFile));

            int[] in = new int[inArchive.getNumberOfItems()];
            for (int i = 0; i < in.length; i++) {
                in[i] = i;
            }
            inArchive.extract(in, false, // Non-test mode
                    new MyExtractCallback(inArchive, path));
        } catch (Exception e) {
            System.err.println("Error occurs: " + e);
            System.exit(1);
        } finally {
            if (inArchive != null) {
                try {
                    inArchive.close();
                } catch (SevenZipException e) {
                    System.err.println("Error closing archive: " + e);
                }
            }
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    System.err.println("Error closing file: " + e);
                }
            }
        }
    }
}