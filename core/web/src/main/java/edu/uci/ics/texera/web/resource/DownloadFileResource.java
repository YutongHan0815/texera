package edu.uci.ics.texera.web.resource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import edu.uci.ics.texera.api.tuple.Tuple;
import edu.uci.ics.texera.dataflow.sink.excel.ExcelSink;
import edu.uci.ics.texera.dataflow.sink.excel.ExcelSinkPredicate;
import edu.uci.ics.texera.dataflow.source.tuple.TupleSourceOperator;

@Path("/download")
@Produces("application/vnd.ms-excel")
@Consumes(MediaType.APPLICATION_JSON)
public class DownloadFileResource {
    
    @GET
    @Path("/result")
    public Response downloadExcelFile(@QueryParam("resultID") String resultID) throws JsonParseException, JsonMappingException, IOException {        
        java.nio.file.Path resultFile = QueryPlanResource.resultDirectory.resolve(resultID + ".json");        

        if (Files.notExists(resultFile)) {
            System.out.println(resultFile + " file does not found");
            return Response.status(Status.NOT_FOUND).build();
        }
        
        
        ArrayList<Tuple> result = new ObjectMapper().readValue(Files.readAllBytes(resultFile), 
                TypeFactory.defaultInstance().constructCollectionLikeType(ArrayList.class, Tuple.class));
        
        if (result.size() == 0) {
            System.out.println(resultFile + " file is empty");
            return Response.status(Status.NOT_FOUND).build();
        }
        
        TupleSourceOperator tupleSource = new TupleSourceOperator(result, result.get(0).getSchema());
        ExcelSink excelSink = new ExcelSinkPredicate().newOperator();
        excelSink.setInputOperator(tupleSource);
        excelSink.open();
        excelSink.collectAllTuples();
        excelSink.close();  
        
        StreamingOutput fileStream = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                byte[] data = Files.readAllBytes(excelSink.getFilePath());
                output.write(data);
                output.flush();
            }
        };
        return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                .header("content-disposition", "attachment; filename=result.xlsx")
                .build();
    }

}
