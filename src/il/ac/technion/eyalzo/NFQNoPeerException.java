package il.ac.technion.eyalzo;


/**
 * An NF Queue 'no peer' exception. Thrown if an attempt is made to use
 * an NFQueue object after it was deleted.
 */
public class NFQNoPeerException extends NFQueueException
{
	private static final long serialVersionUID = 1L;

	NFQNoPeerException(String description)
    {
        super(description);
    }
}

