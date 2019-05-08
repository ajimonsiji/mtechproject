package il.ac.technion.eyalzo;


/**
 * An NF Queue exception.
 */
public class NFQueueException extends Exception
{
	private static final long serialVersionUID = 1L;

	NFQueueException(String description)
    {
        super(description);
    }
}

