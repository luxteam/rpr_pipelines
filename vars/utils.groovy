class utils {

	static int getTimeoutFromXML(Object self, String test, String keyword, Integer additional_xml_timeout) 
	{
		try {
			String xml = self.readFile("jobs/Tests/${test}/test.job-manifest.xml")
	        for (xml_string in xml.split("<")) {
	            if (xml_string.contains("${keyword}")) {
	            	Integer xml_timeout = (Math.round((xml_string.findAll(/\d+/)[0] as Double).div(60)) + additional_xml_timeout)
	                return xml_timeout
	            }
	        }
	    } catch (e) {
	    	self.println(e)
	        return -1
	    }
		
	}

}