/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.wisterious.labchecker;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author thomas
 */
public abstract class ChildProcess {
    final Process p;
    Set<ChildProcessListener> cpl;
    Thread t;

    public ChildProcess(Process parg) {
        this.p = parg;
        cpl = new HashSet<ChildProcessListener>();
        this.t = new Thread() {
          public void run() {
              runBody();
              try {
                p.waitFor();
              }
              catch(Exception e) {
                p.destroy();
              }
              informDone();
          }
        };
    }

    public abstract void runBody();

    public void start() {
        t.start();
    }

    private void informDone() {
        for(ChildProcessListener c : cpl) {
            c.processDone(p.exitValue());
        }
    }

    public void addListener(ChildProcessListener l) {
        cpl.add(l);
    }

    public void removeListener(ChildProcessListener l) {
        cpl.remove(l);
    }

    public void kill() {
        p.destroy();
    }
}
