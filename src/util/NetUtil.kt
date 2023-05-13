package util

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object NetUtil
{
    fun downloadTileImage(url: URL, streamAction: (InputStream) -> Unit, onError: ((Throwable) -> Unit) = {})
    {
        val con: HttpURLConnection = url.openConnection() as HttpURLConnection

        con.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.2; .NET CLR 1.0.3705;)")

        con.requestMethod = "GET"
        con.connectTimeout = 5000
        con.readTimeout = 5000

        val responseCode: Int
        try
        {
            responseCode = con.responseCode
        }
        catch(e: IOException)
        {
            onError(e)
            return
        }
        if(responseCode != 200)
        {
            onError(IllegalStateException("Response code is not 200"))
            return
        }

        val inputStream: InputStream
        try
        {
            inputStream = con.inputStream
        }
        catch (e: Exception)
        {
            onError(e)
            return
        }
        try
        {
            streamAction(inputStream)
        }
        catch(e: Throwable)
        {
            onError(e)
            return
        }
        finally
        {
            try
            {
                inputStream.close()
            }
            catch(e: IOException)
            {
                onError(e)
            }
        }
    }
}