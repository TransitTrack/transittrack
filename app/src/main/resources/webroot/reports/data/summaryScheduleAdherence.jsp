<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.transitclock.api.reports.ScheduleAdherenceController" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.ParseException" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Date" %><%@ page import="java.util.List"%><%@ page import="java.util.Map"%>

<%@ page contentType="application/json" %>
<%
// todo this code should be in a struts action
String startDateStr = request.getParameter("beginDate");
String numDaysStr = request.getParameter("numDays");
String startTime = request.getParameter("beginTime");
String endTime = request.getParameter("endTime");
String earlyLimitStr = request.getParameter("allowableEarly");
String lateLimitStr = request.getParameter("allowableLate");
Double earlyLimit = -60.0;
Double lateLimit = 60.0;

if (StringUtils.isEmpty(startTime))
	startTime = "00:00:00";
else
	startTime += ":00";

if (StringUtils.isEmpty(endTime))
	endTime = "23:59:59";
else
	endTime += ":00";

if (!StringUtils.isEmpty(earlyLimitStr)) {
	earlyLimit = Double.parseDouble(earlyLimitStr) * -60;
}
if (!StringUtils.isEmpty(lateLimitStr)) {
	lateLimit = Double.parseDouble(lateLimitStr) * 60;
}


String routeIdList = request.getParameter("r");
List<String> routeIds = routeIdList == null ? null : Arrays.asList(routeIdList.split(","));
Date startDate = null;
        try {
            if (startDateStr.charAt(4) != '-') {
        DateFormat defaultDateFormat = new SimpleDateFormat("MM-dd-yyyy");
                startDate = new Timestamp(defaultDateFormat.parse(startDateStr).getTime());
            } else {
        DateFormat altDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                startDate = new Timestamp(altDateFormat.parse(startDateStr).getTime());
            }
        } catch (ParseException e) {
           e.printStackTrace();
        }

Map<String, String> results = ScheduleAdherenceController.routeScheduleAdherenceSummary(startDate,
		Integer.parseInt(numDaysStr), startTime, endTime, earlyLimit, lateLimit, routeIds);

response.setHeader("Access-Control-Allow-Origin", "*");
JSONObject json = new JSONObject(results);
json.write(out);

%>
