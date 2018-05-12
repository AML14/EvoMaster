package org.evomaster.clientJava.controller.internal.db;

import io.restassured.http.ContentType;
import org.evomaster.clientJava.controller.InstrumentedSutStarter;
import org.evomaster.clientJava.controller.db.DatabaseTestTemplate;
import org.evomaster.clientJava.controller.db.SqlScriptRunner;
import org.evomaster.clientJava.controllerApi.dto.database.*;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.evomaster.clientJava.controllerApi.ControllerConstants.BASE_PATH;
import static org.evomaster.clientJava.controllerApi.ControllerConstants.INFO_SUT_PATH;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaExtractorTest extends DatabaseTestTemplate {


    @Test
    public void testBasic() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT)");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());
        assertNotNull(schema);

        assertAll(() -> assertEquals("public", schema.name.toLowerCase()),
                () -> assertEquals(DatabaseType.H2, schema.databaseType),
                () -> assertEquals(1, schema.tables.size()),
                () -> assertEquals("foo", schema.tables.get(0).name.toLowerCase()),
                () -> assertEquals(1, schema.tables.get(0).columns.size())
        );
    }

    @Test
    public void testTwoTables() throws Exception {
        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT); CREATE TABLE Bar(y INT)");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());
        assertNotNull(schema);

        assertEquals(2, schema.tables.size());
        assertTrue(schema.tables.stream().map(t -> t.name.toLowerCase()).anyMatch(n -> n.equals("foo")));
        assertTrue(schema.tables.stream().map(t -> t.name.toLowerCase()).anyMatch(n -> n.equals("bar")));
    }


    @Test
    public void testIdentity() throws Exception {
        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", x int" +
                ", primary key (id) " +
                ");");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        TableDto table = schema.tables.get(0);
        assertEquals(2, table.columns.size());

        ColumnDto id = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("id"))
                .findAny().get();
        ColumnDto x = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("x"))
                .findAny().get();

        assertEquals("integer", x.type.toLowerCase());
        assertEquals("bigint", id.type.toLowerCase());

        assertFalse(x.autoIncrement);
        assertTrue(id.autoIncrement);
    }


    @Test
    public void testBasicConstraints() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", name varchar(128) not null " +
                ", surname varchar(255) " +
                ", primary key (id) " +
                ");");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        TableDto table = schema.tables.get(0);
        assertEquals(3, table.columns.size());

        ColumnDto id = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("id"))
                .findAny().get();
        assertTrue(id.autoIncrement);

        ColumnDto name = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("name"))
                .findAny().get();
        assertFalse(name.autoIncrement);
        assertFalse(name.nullable);
        assertEquals(128, name.size);

        ColumnDto surname = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("surname"))
                .findAny().get();
        assertFalse(surname.autoIncrement);
        assertTrue(surname.nullable);
        assertEquals(255, surname.size);
    }



    @Test
    public void testBasicForeignKey() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", barId bigint not null " +
                ");" +
                " CREATE TABLE Bar(id bigint generated by default as identity);" +
                " ALTER TABLE Foo add constraint barIdKey foreign key (barId) references Bar;\n"
        );

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());
        assertEquals(2, schema.tables.size());

        TableDto bar = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Bar")).findAny().get();
        TableDto foo = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(0, bar.foreignKeys.size());
        assertEquals(1, foo.foreignKeys.size());

        ForeignKeyDto foreignKey = foo.foreignKeys.get(0);

        assertEquals(1, foreignKey.columns.size());
        assertTrue(foreignKey.columns.stream().anyMatch(c -> c.equalsIgnoreCase("barId")));
        assertTrue(foreignKey.targetTable.equalsIgnoreCase("Bar"));
    }

    @Test
    public void testQuizGame() throws Exception {

        SqlScriptRunner.runScriptFromResourceFile(getConnection(), "/db_schemas/quizgame.sql");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());
        assertEquals(6, schema.tables.size());

        //TODO test all of its content
    }

    @Test
    public void testRetrieveSchema() throws Exception{

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT); CREATE TABLE Bar(y INT)");

        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        String url = start(starter);

        given().accept(ContentType.JSON)
                .get(url + BASE_PATH + INFO_SUT_PATH)
                .then()
                .statusCode(200)
                .body("sqlSchemaDto.tables.size()", is(2));
    }
}