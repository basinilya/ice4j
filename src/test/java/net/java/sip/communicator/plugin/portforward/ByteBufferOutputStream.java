package net.java.sip.communicator.plugin.portforward;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class ByteBufferOutputStream
    extends OutputStream
{
    private final ByteBuffer buffer;

    ByteBufferOutputStream(ByteBuffer buffer)
    {
        this.buffer = buffer;
    }

    public void write(int b) throws IOException
    {
        buffer.put((byte) b);
    }
}
