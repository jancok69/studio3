/**
 * Aptana Studio
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the GNU Public License (GPL) v3 (with exceptions).
 * Please see the license.html included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package com.aptana.core.util;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllUtilTests
{

	public static Test suite()
	{
		TestSuite suite = new TestSuite("Test for com.aptana.core.util");
		// $JUnit-BEGIN$
		suite.addTestSuite(ArrayUtilTest.class);
		suite.addTestSuite(ClassUtilTest.class);
		suite.addTestSuite(CollectionsUtilTest.class);
		suite.addTestSuite(EclipseUtilTest.class);
		suite.addTestSuite(FileUtilTest.class);
		suite.addTestSuite(FirefoxUtilTest.class);
		suite.addTestSuite(IOUtilTest.class);
		suite.addTestSuite(RegexUtilTest.class);
		suite.addTestSuite(ResourceUtilTest.class);
<<<<<<< HEAD
		suite.addTestSuite(OutputStreamThreadTest.class);
=======
		suite.addTestSuite(SourcePrinterTest.class);
>>>>>>> 397c3b125e9d005970af30b0bbf1ae0d3a94b9be
		suite.addTestSuite(StreamUtilTest.class);
		suite.addTestSuite(StringUtilTest.class);
		suite.addTestSuite(TimeZoneUtilTest.class);
		suite.addTestSuite(VersionUtilTest.class);
		suite.addTestSuite(URLUtilTest.class);
		suite.addTestSuite(ZipUtilTest.class);
		suite.addTestSuite(WriterOutputStreamTest.class);
		suite.addTestSuite(InputStreamGobblerTest.class);
		// $JUnit-END$
		return suite;
	}
}
