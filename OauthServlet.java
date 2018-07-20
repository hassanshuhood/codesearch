package oracle.apps.hed.campusCommunity.shared.common.publicUi.servlet;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.io.OutputStreamWriter;

import java.net.HttpURLConnection;
import java.net.URL;

import java.net.URLEncoder;

import java.security.AccessController;

import java.text.SimpleDateFormat;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.el.PropertyNotFoundException;

import javax.security.auth.Subject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import oracle.apps.fnd.applcore.common.ApplSession;
import oracle.apps.fnd.applcore.common.ApplSessionUtil;
import oracle.apps.fnd.applcore.common.SecuredTokenBean;
import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.apps.cdm.foundation.publicModel.common.util.HzSessionUtil;

import oracle.apps.fnd.applcore.Profile;
import oracle.apps.fnd.applcore.messages.ApplcoreException;

import oracle.security.jps.util.SubjectUtil;

import org.json.JSONException;
import org.json.JSONObject;


public class HedTokenGenerationServlet extends HttpServlet {
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final String SUCCESS_STATUS_MESSAGE = "success";
    private static final String ERROR_STATUS_MESSAGE = "token_servlet_error";
    private static final String URL_PARAM_REDIRECT_URI = "redirect_uri";
    private static final String URL_PARAM_STATE = "state";
    private static final long TOKEN_DURATION = 1000*60*60*4;
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss z";
    private static final String TIMEZONE = "GMT";
    private static String[] m_propertyNames =
    { "USERNAME", "CURRENTLANGUAGE", "NLSLANGUAGECODE", "NLSLANGUAGESTRING",
      "NLSSORTBEHAVIOUR", "DATEFORMAT", "TIMEFORMAT", "GROUPINGSEPERATOR",
      "DECIMALSEPERATOR", "CURRENCY", "TIMEZONE", "USERDISPLAYNAME",
      "NUMBERFORMAT" };
    
    /**
     * Get the current ApplSession for the user
     * @return Current ApplSession
     */
    private static ApplSession getSessionInstance() {
        ApplSession applSession = ApplSessionUtil.getSession();
        return applSession;
    }

    /**
     * Get the user preferences using the current ApplSession and the list of properties to be fetched m_propertyNames
     * @return Map containing the user preferences
     */
    private Map getUserPreferences() {
        AppsLogger.write(this, "Getting User Preferences", AppsLogger.FINEST);
        ApplSession session = getSessionInstance();
        Map<String, Object> userPreferences = new HashMap<String, Object>();
        for (String property : m_propertyNames) {
            Object sessionValue = this.getProperty(session, property);
            userPreferences.put(property, sessionValue);
        }
        return userPreferences;

    }

    /**
     * Extract a user preference property based on current session and property name
     * @param session Session for whose user preferences are to be extracted
     * @param propName Name of user preference property to be extracted
     * @return user preference value for the input session and property name
     */
    private Object getProperty(ApplSession session, String propName) {

        if (isPropertySupported(propName)) {

            if (session != null) {

                if ("CURRENCY".equals(propName))
                    return ApplSessionUtil.getCurrency();
                else if ("CURRENTLANGUAGE".equals(propName))
                    return session.getLanguage();
                else if ("TIMEZONE".equals(propName))
                    return session.getTimeZone();
                else if ("USERNAME".equals(propName)) {
                    String userName = session.getUserName();
                    if (userName == null || "".equals(userName))
                        userName = "EMPTY_TOKEN";
                    return userName;
                } else if ("NLSLANGUAGECODE".equals(propName))
                    return session.getNLSLang();
                else if ("NLSLANGUAGESTRING".equals(propName))
                    return session.getNLSLanguage();
                else if ("NLSSORTBEHAVIOUR".equals(propName))
                    return session.getNLSSort();
                else if ("DATEFORMAT".equals(propName))
                    return session.getDateFormat();
                else if ("TIMEFORMAT".equals(propName))
                    return session.getTimeFormat();
                else if ("GROUPINGSEPERATOR".equals(propName))
                    return session.getGroupingSeparator(); //Char
                else if ("DECIMALSEPERATOR".equals(propName))
                    return session.getDecimalSeparator(); //Char
                else if ("USERDISPLAYNAME".equals(propName))
                    return session.getUserDisplayName();
                else if ("NUMBERFORMAT".equals(propName))
                    return session.getNumberFormat();

            } else
                throw new ApplcoreException("FND::FND_DIAGLOG_MESSAGE_5");


        } else
            throw new PropertyNotFoundException(propName);

        return null;
    }

    /**
     * Checks if the given user preference property name is valid
     * @param propName user preference property name to be checked for existence in m_propertyNames
     * @return true if property exists else false
     */
    private boolean isPropertySupported(String propName) {
       List propNamesList = Arrays.asList(m_propertyNames);
       return propNamesList.contains(propName);
    }
    
    /**
     * Write output in servlet's response
     * @param response servlet response object
     * @param in data to write as output to servlet response
     */
    private void writeToOutputStream(HttpServletResponse response,
                                     BufferedInputStream in) {
        if (in != null) {
            OutputStream out = null;
            try {
                out = response.getOutputStream();

                byte[] chunk = new byte[8192];
                int bytesRead = 0;
                while ((bytesRead = in.read(chunk, 0, 8192)) != -1) {
                    out.write(chunk, 0, bytesRead);
                }
                out.close();
            } catch (IOException e) {
                AppsLogger.write(this, e);
            } finally {
                try {
                    if (out != null)
                        out.close();
                } catch (IOException ioe) {
                    AppsLogger.write(this, ioe);
                }
            }
        }
    }
    
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }
 
    /**Process the HTTP doGet request.
     */
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response) throws ServletException,
                                                           IOException {
        
        AppsLogger.write(this, "Token Generation Servlet accessed", AppsLogger.FINEST);
        JSONObject json = new JSONObject();
        Subject subject = Subject.getSubject(AccessController.getContext());
        String userName = SubjectUtil.getUserName(subject);
        Map parameters = request.getParameterMap();
        String redirectURI = null;
        String state = null;
        String status = null;
        String token = null;
        String timeStamp = null;
        String ibcsHost = Profile.get("IBCSHOST");
        String ibcsPort = Profile.get("IBCSPORT");
        
        if(parameters.containsKey(URL_PARAM_REDIRECT_URI) && ((String[])parameters.get(URL_PARAM_REDIRECT_URI)).length > 0) {
            redirectURI = ((String[])parameters.get(URL_PARAM_REDIRECT_URI))[0];
        }
        if(parameters.containsKey(URL_PARAM_STATE) && ((String[])parameters.get(URL_PARAM_STATE)).length > 0) {
            state = ((String[])parameters.get(URL_PARAM_STATE))[0];
        }
        AppsLogger.write(this, "Token generated for user "+ userName, AppsLogger.INFO);
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone(TIMEZONE));
        try {
            timeStamp = dateFormat.format(new Date());
            token = (new SecuredTokenBean()).getTrustToken();
            status = SUCCESS_STATUS_MESSAGE;
        } catch (Exception e) {
            AppsLogger.write(this, e, AppsLogger.SEVERE);
            status = ERROR_STATUS_MESSAGE;
        }
        
        if (redirectURI != null) {
//            System.out.println("\n\n\n\n  Host----" + ibcsHost);
//            System.out.println("\n\n\n\n  Port----" + ibcsPort);
//            System.out.println("http://" + ibcsHost + ":" + ibcsPort + "/connectors/v1/callback?state=" + URLEncoder.encode(state, "UTF-8"));
            URL url = new URL("http://" + ibcsHost + ":" + ibcsPort + "/connectors/v1/callback?state=" + URLEncoder.encode(state, "UTF-8"));
            json = new JSONObject();
            HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setDoOutput(true);
            OutputStreamWriter connOutStream = new OutputStreamWriter(httpConnection.getOutputStream());
            
            httpConnection.setRequestMethod("POST");
            httpConnection.setRequestProperty("Content-Type", "application/json");
            
            try {
                json.put("token", token);
            } catch (JSONException e) {
                AppsLogger.write(this, e, AppsLogger.SEVERE);
            }
            
//            System.out.println(" \n\n\n\n\n Making POST call at "+ url.toString() + "with output content " + json.toString());
            AppsLogger.write(this, "Making POST call at "+ url.toString() + "with output content " + json.toString(), AppsLogger.INFO);
            connOutStream.write(json.toString());
            connOutStream.flush();
            AppsLogger.write(this, httpConnection.getResponseMessage(), AppsLogger.INFO);
//            System.out.println(httpConnection.getResponseCode());
//            System.out.println(httpConnection.getResponseMessage());
            connOutStream.close();
        } else {
            try {
                json.put("user", userName);
                json.put("status", status);
                json.put("timeStamp", timeStamp);
                json.put("partyId", HzSessionUtil.getUserPartyId());
                if (status.equals(SUCCESS_STATUS_MESSAGE)){
                 json.put("tokenDuration", TOKEN_DURATION);// in seconds = 4 hour
                 json.put("token", token);
                 json.put("preferences", getUserPreferences());
                }
            } catch (JSONException e) {
                AppsLogger.write(this, e, AppsLogger.SEVERE);
                status = ERROR_STATUS_MESSAGE;
            }
            response.setContentType(CONTENT_TYPE);
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("X-Content-Type-Options", "nosniff"); 
            response.setHeader("Expires", "-1");
            writeToOutputStream(response, new BufferedInputStream(new ByteArrayInputStream(json.toString().getBytes())));
        }
    }
}
