/*
 * Copyright (C) 2014 Michell Bak
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.test.smbstreamer.variant1;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import jcifs.smb.SmbFile;
import android.util.Log;

public class Streamer extends StreamServer {

	public static final int PORT = 7871;
	public static final String URL = "http://127.0.0.1:" + PORT;
	private SmbFile file;
	protected List<SmbFile> extras; // those can be subtitles
	private static Streamer instance;

	protected Streamer(int port) throws IOException {
		super(port, new File("."));
	}

	public static Streamer getInstance() {
		if (instance == null)
			try {
				instance = new Streamer(PORT);
			} catch (IOException e) {
				e.printStackTrace();
			}
		return instance;
	}

	public void setStreamSrc(SmbFile file,List<SmbFile> extraFiles) {
		this.file = file;
		this.extras = extraFiles;
	}

	@Override
	public Response serve(String uri, String method, Properties header, Properties parms, Properties files) {
		Response res = null;
		try {
			SmbFile sourceFile = null;
			String name = getNameFromPath(uri);
			if(file!=null && file.getName().equals(name))
				sourceFile = file;
			else if(extras!=null){
				for(SmbFile i : extras){
					if(i!=null && i.getName().equals(name)){
						sourceFile = i;
						break;
					}
				}
			}
			if (sourceFile==null)
				res= new Response(HTTP_NOTFOUND, MIME_PLAINTEXT, null);
			else {
				long startFrom = 0;
				long endAt = -1;
				String range = header.getProperty("range");
				if (range != null) {
					if (range.startsWith("bytes=")) {
						range = range.substring("bytes=".length());
						int minus = range.indexOf('-');
						try {
							if (minus > 0) {
								startFrom = Long.parseLong(range.substring(0, minus));
								endAt = Long.parseLong(range.substring(minus + 1));
							}
						} catch (NumberFormatException nfe) {}						
					}
				}
				Log.d("Streamer", "Request: " + range + " from: " + startFrom + ", to: " + endAt);

				// Change return code and add Content-Range header when skipping is requested
				final StreamSource source = new StreamSource(sourceFile);
				long fileLen = source.length();
				if (range != null && startFrom > 0) {
					if (startFrom >= fileLen) {
						res = new Response(HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, null);
						res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
					} else {
						if (endAt < 0)
							endAt = fileLen - 1;
						long newLen = fileLen - startFrom;
						if (newLen < 0)
							newLen = 0;
						Log.d("Streamer", "start=" + startFrom + ", endAt=" + endAt + ", newLen=" + newLen);
						final long dataLen = newLen;
						source.moveTo(startFrom);
						Log.d("Streamer", "Skipped " + startFrom + " bytes");

						res = new Response(HTTP_PARTIALCONTENT, source.getMimeType(), source);
						res.addHeader("Content-length", "" + dataLen);
						res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
					}
				} else {
					source.reset();
					res = new Response(HTTP_OK, source.getMimeType(), source);
					res.addHeader("Content-Length", "" + fileLen);
				}
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, null);
		}

		// Announce that the file server accepts partial content requestes
		res.addHeader("Accept-Ranges", "bytes");
		return res;
	}

	public static String getNameFromPath(String path){
		if(path == null || path.length() < 2)
			return null;
		int slash = path.lastIndexOf('/');
		if(slash == -1)
			return path;
		return path.substring(slash+1);
	}

}