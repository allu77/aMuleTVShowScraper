package com.iukonline.amule.aMuleTVShowScraper;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;

import com.iukonline.amule.ec.ECSearchFile;
import com.iukonline.amule.ec.ECSearchResults;
import com.iukonline.amule.ec.ECUtils;
import com.iukonline.amule.ec.exceptions.ECClientException;
import com.iukonline.amule.ec.exceptions.ECPacketParsingException;
import com.iukonline.amule.ec.exceptions.ECServerException;
import com.iukonline.amule.ec.v204.ECClientV204;
import com.iukonline.amule.ec.v204.ECCodesV204;


public class aMuleTVShowScraper {
    
    final static int WAIT_INTERVAL_SEC = 5;
    final static int SEARCH_TIMEOUT_SEC = 60;
    final static int ED2K_CONNECT_TIMEOUT_SEC = 120;

    
    final static String WORD_SEPARATORS_REGEX = "[\\s._\\-']+";
    final static String[] EPISODE_FORMAT = { "%dx%02d", "%02dx%02d", "s%02de%02d" };
    final static byte[] SEARCH_TYPE = { ECCodesV204.EC_SEARCH_GLOBAL, ECCodesV204.EC_SEARCH_KAD };
    static HashMap<String, String[]> langMap = new HashMap<String, String[]>();
    final static String[] LANG_MAP_ITA = { 
        "(?<!\\bsub(bed)?\\b)" + WORD_SEPARATORS_REGEX + "\\bita\\b",  
        "(?<!\\bsub\\b)" + WORD_SEPARATORS_REGEX + "\\bitalian\\b" + WORD_SEPARATORS_REGEX + "(?!\\bsubbed\\b)",
        "(?<!\\bsottotitoli\\b(" + WORD_SEPARATORS_REGEX  +"\\bin\\b)?)" + WORD_SEPARATORS_REGEX + "\\bitaliano\\b"
    }; 
    

    /**
     * @param args
     * @throws NoSuchAlgorithmException 
     * @throws IOException 
     * @throws UnknownHostException 
     * @throws ECServerException 
     * @throws ECPacketParsingException 
     * @throws ECClientException 
     * @throws InterruptedException 
     */
    public static void main(String[] argv) throws NoSuchAlgorithmException, UnknownHostException, IOException, ECClientException, ECPacketParsingException, ECServerException, InterruptedException {

        langMap.put("ita", LANG_MAP_ITA);
        
        String lang = null;
        String res = null;
        int port = 4712;
        long minSize = 0L;
        long maxSize = -1L;
        PrintStream ed2kOut = System.out;
        
        
        
        int i = 0;
        while (i < argv.length) {
            if (argv[i].equals("-l") && i + 1 < argv.length) {
                lang = argv[++i];
            } else if (argv[i].equals("-r") && i + 1 < argv.length) {
                res = argv[++i];
            } else if (argv[i].equals("-p") && i + 1 < argv.length) {
                port = Integer.parseInt(argv[++i]);
            } else if (argv[i].equals("-m") && i + 1 < argv.length) {
                minSize = Long.parseLong(argv[++i]);
            } else if (argv[i].equals("-M") && i + 1 < argv.length) {
                maxSize = Long.parseLong(argv[++i]);
            } else if (argv[i].equals("-o") && i + 1 < argv.length) {
                String fileOut = argv[++i];
                if (!fileOut.equals("-")) {
                    ed2kOut = new PrintStream(new File(fileOut));
                }
            } else if (argv[i].startsWith("-")) {
                System.err.println("Invalid option " + argv[i]);
                return;
            } else {
                break;
            }
            i++;
        }
        
        if (argv.length - i != 5) {
            System.err.println("Invalid parameter count");
            return;
        }
       
        ECClientV204 cl = new ECClientV204();
        cl.setClientName("aMuleTVShowScraper");
        cl.setClientVersion("alpha");
        cl.setPassword(argv[i + 1]);
        cl.setSocket(new Socket(argv[i], port));
        
        //cl.setTracer(System.out);

        String title = argv[i + 2];
        int season = Integer.parseInt(argv[i + 3]);
        int episode = Integer.parseInt(argv[i + 4]);

        ArrayList <ECSearchFile> good = new ArrayList<ECSearchFile>();

        doSearch(cl, good, title, season, episode, lang, res, minSize, maxSize);
        Collections.sort(good, new compareResult());
        Collections.reverse(good);
        
        for (ECSearchFile sf : good) {
            String fileName = sf.getFileName();
            long size = sf.getSizeFull();
            byte[] hash = sf.getHash();
            StringBuilder sb = new StringBuilder("ed2k://|file|" + fileName.replaceAll(" ", "%20") + "|");
            sb.append(Long.toString(size) + "|");
            sb.append(ECUtils.byteArrayToHexString(hash, hash.length, 0, ""));
            sb.append("|/");
            ed2kOut.println(sb.toString());
        }
    }
    
    private static void doSearch(ECClientV204 cl, ArrayList <ECSearchFile> files, String title, int season, int episode, String lang, String res, long minSize, long maxSize) throws ECClientException, IOException, ECPacketParsingException, ECServerException, InterruptedException {
        
        String searchTitle = "";
        for (String word : title.split(WORD_SEPARATORS_REGEX)) {
            if (word.length() > 2) {
                searchTitle += searchTitle.length() > 0 ? " " + word : word;
            }
        }

        for (byte searchType : SEARCH_TYPE) {
            
            if (searchType == ECCodesV204.EC_SEARCH_GLOBAL || searchType == ECCodesV204.EC_SEARCH_LOCAL) {
                int waitSec = 0;
                while (cl.getStats().getConnState().isConnectingEd2k() && waitSec < ED2K_CONNECT_TIMEOUT_SEC) {
                    System.out.format("\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\baMule is connecting to ED2K network, waiting [%3d/%3d]", waitSec, ED2K_CONNECT_TIMEOUT_SEC);
                    waitSec += WAIT_INTERVAL_SEC;
                    Thread.sleep(WAIT_INTERVAL_SEC * 1000);
                }
                if (waitSec > 0) {
                    System.out.print("\n");
                }
                if (! cl.getStats().getConnState().isConnectedEd2k()) {
                    System.out.println("Not connected to any ED2K server. Skipping ED2K search.");
                    continue;
                }
            }
            
            for (String formatEp : EPISODE_FORMAT) {
                
                String searchString = searchTitle + " " + String.format(formatEp, season, episode);
                if (lang != null) searchString = searchString + " " + lang;
    
                System.out.print("Searching for " + searchString + "                ");
                cl.searchStart(searchString, "", "", minSize, maxSize, -1, searchType);
                ECSearchResults result = null;
                byte progress = 0;
                byte lastProgress = 0;
                int runningSec = 0;
                while (progress < 100 && progress >= lastProgress && runningSec < SEARCH_TIMEOUT_SEC) {
                    if (runningSec > 0) Thread.sleep(WAIT_INTERVAL_SEC * 1000);

                    result = cl.searchGetReults(result);
                    progress = cl.searchProgress();

                    if (progress == 100 && runningSec == 0) {
                        progress = 0;
                    } else if (progress < lastProgress) {
                        progress = lastProgress;
                    }
                    
                    lastProgress = progress;
                    System.out.format("\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b%3d%% - %3d/%3ds", progress, runningSec, SEARCH_TIMEOUT_SEC);

                    runningSec += WAIT_INTERVAL_SEC;

                }
                cl.searchStop();
                cl.searchProgress();
                System.out.println("");
                result = cl.searchGetReults(result);
                
                if (! result.resultMap.isEmpty()) {
                    Iterator<ECSearchFile> i = result.resultMap.values().iterator();
                    StringBuilder sb = new StringBuilder("(?i)" + title.replaceAll("[\\s\\._\\-']+", Matcher.quoteReplacement("\\b" + WORD_SEPARATORS_REGEX + "\\b")) + "\\b" + WORD_SEPARATORS_REGEX);
                    sb.append("\\b" + String.format(formatEp, season, episode) + "\\b");
                    sb.append(".*\\.(avi|mkv|mpg|mpeg|mp4|mov|3gpp)");
                    String regex = sb.toString().replaceAll("\\\\b&\\\\b", "&"); // Word bonduaries don't work around &
                    System.out.println("    Testing result with regex " + regex + "\n");
                    while (i.hasNext()) {
                        ECSearchFile sf = i.next();
                        String fileName = sf.getFileName();
                        System.out.print("    File " + fileName + " ... ");
                        
                        if (fileName.matches(regex)) {
                            System.out.print("Matches!");
                            
                            boolean goOn = true; 
                            if (lang != null && langMap.containsKey(lang)) {
                                System.out.print(" Checking language " + lang + "...");
                                goOn = false;
                                for (String langRegex : langMap.get(lang)) {
                                    //System.out.println("Testing against " + langRegex);
                                    if (fileName.matches("(?i).*" + langRegex + ".*")) {
                                        System.out.print(" Matches!");
                                        goOn = true;
                                        break;
                                    }
                                }
                                if (!goOn) {
                                    System.out.println(" Skipping! :(");
                                }
                            }
                            
                            if (goOn && res != null) {
                                System.out.print(" Checking resolution " + res + "...");
                                if (res.equalsIgnoreCase("sd")) {
                                    goOn = ! fileName.matches("(?i).*(1080p|1080i|720p).*");
                                } else if (res.equals("720p")) {
                                    goOn = fileName.matches("(?i).*720p.*");
                                } else if (res.equals("720p+")) {
                                    goOn = fileName.matches("(?i).*(1080p|1080i|720p).*");
                                } else if (res.equals("1080i")) {
                                    goOn = fileName.matches("(?i).*1080i.*");
                                } else if (res.equals("1080i+")) {
                                    goOn = fileName.matches("(?i).*(1080p|1080i).*");
                                } else if (res.equals("1080p")) {
                                    goOn = fileName.matches("(?i).*1080p.*");
                                }
                                if (goOn) System.out.print(" Matches!");
                                else System.out.println(" Skipping! :(");
                            }
                            
                            if (goOn) {
                                System.out.println(" Valid! :)");
                                files.add(sf);
                            }
                        } else {
                            System.out.println(" Skipping! :(");
                        }
                    }
                }
            }
            
        }
        
    }
    
    public static class compareResult implements Comparator<ECSearchFile> {

        @Override
        public int compare(ECSearchFile o1, ECSearchFile o2) {
            return o1.getSourceCount() - o2.getSourceCount();
        }
        
    }

}
