/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.esotericsoftware.kryonet;

import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.serialization.KryoSerialization;

/**
 * Represents the local end point of a connection.
 *
 * @author Nathan Sweet &lt;misc@n4te.com&gt;
 */
public interface EndPoint extends Runnable {

	/**
	 * Adds a listener to the connection. If the given listener was already
	 * added before, it is ignored.
	 * 
	 * @param listener
	 *            The listener to add. Cannot be <code>null</code>.
	 */
	void addListener(Listener listener);

	/**
	 * Removes a previously added listener.
	 * 
	 * @param listener
	 *            The listener to remove. Cannot be <code>null</code>.
	 */
	void removeListener(Listener listener);

	/**
	 * Continually updates this end point until {@link #stop()} is called.
	 */
	@Override
	void run();

	/**
	 * Starts a new thread that calls {@link #run()}.
	 */
	void start();

	/**
	 * Closes this end point and causes {@link #run()} to return.
	 */
	void stop();

	/**
	 * @see Client
	 * @see Server
	 */
	void close();

	/**
	 * @see Client#update(int)
	 * @see Server#update(int)
	 */
	void update(int timeout) throws IOException;

	/**
	 * Returns the last thread that called {@link #update(int)} for this end
	 * point. This can be useful to detect when long running code will be run on
	 * the update thread.
	 */
	Thread getUpdateThread();

	/**
	 * Gets the {@linkplain Kryo} instance that will be used to serialize and
	 * deserialize objects. Returns <code>null</code> if the used serialization
	 * is not an instance of {@link KryoSerialization}.
	 * 
	 * @return May be <code>null</code>.
	 */
	Kryo getKryo();
}
