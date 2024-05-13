package com.pocdemo.exec;

import com.pocdemo.jdbc.orm.ORMHelper;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@Path("/test")
public class Test {
    @Path("/invoke")
    @Consumes(MediaType.TEXT_PLAIN)
    public void generatetDslScript(String sql) throws Exception {
        var c = ORMHelper.conn();
        ORMHelper.exec(sql);
    }
}
