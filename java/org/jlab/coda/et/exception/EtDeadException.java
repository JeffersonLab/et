package org.jlab.coda.et.exception;

/**
 * This class represents an error of an ET system when its processes are dead.
 *
 * @author Carl Timmer
 */
public class EtDeadException extends Exception {

    /**
     * Create an exception indicating an error of an ET system when its processes are dead.
     * {@inheritDoc}<p/>
     *
     * @param message {@inheritDoc}<p/>
     */
    public EtDeadException(String message) {
        super(message);
    }

}
