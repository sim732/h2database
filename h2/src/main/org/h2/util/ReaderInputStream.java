/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html). 
 * Initial Developer: H2 Group 
 */
package org.h2.util;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.SQLException;

import org.h2.engine.Constants;
import org.h2.message.Message;

/**
 * @author Thomas
 */

public class ReaderInputStream extends InputStream {
    
    private final Reader reader;
    private final char[] chars;
    private final ByteArrayOutputStream out;
    private final Writer writer;
    private int pos;
    private int remaining;
    private byte[] buffer;
    
    public ReaderInputStream(Reader reader) throws SQLException {
        chars = new char[Constants.IO_BUFFER_SIZE];
        this.reader = reader;
        out = new ByteArrayOutputStream(Constants.IO_BUFFER_SIZE);
        try {
            writer = new BufferedWriter(new OutputStreamWriter(out, Constants.UTF8));
        } catch (UnsupportedEncodingException e) {
            throw Message.convert(e);
        }
    }
    
    private void fillBuffer() throws IOException {
        if (remaining == 0) {
            pos = 0;
            remaining = reader.read(chars, 0, Constants.IO_BUFFER_SIZE);
            if (remaining < 0) {
                return;
            }          
//            String s = new String(chars, 0, remaining);
//            try {
//                buffer = StringUtils.asBytes(s);
//            } catch(SQLException e) {
//                throw new IOException(e.toString());
//            }
            writer.write(chars, 0, remaining);
            writer.flush();
            buffer = out.toByteArray();
            remaining = buffer.length;
            out.reset();
        }
    }

    public int read() throws IOException {
        if (remaining == 0) {
            fillBuffer();
        }
        if (remaining < 0) {
            return -1;
        }
        remaining--;
        return buffer[pos++] & 0xff;
    }

}
