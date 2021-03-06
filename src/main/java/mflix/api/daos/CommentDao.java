package mflix.api.daos;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.ReadConcern;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Comment;
import mflix.api.models.Critic;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.mongodb.client.model.Updates.set;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Component
public class CommentDao extends AbstractMFlixDao {

    public static String COMMENT_COLLECTION = "comments";
    private final Logger log;
    private MongoCollection<Comment> commentCollection;
    private CodecRegistry pojoCodecRegistry;

    @Autowired
    public CommentDao(
            MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
        super(mongoClient, databaseName);
        log = LoggerFactory.getLogger(this.getClass());
        this.db = this.mongoClient.getDatabase(MFLIX_DATABASE);
        this.pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        this.commentCollection =
                db.getCollection(COMMENT_COLLECTION, Comment.class).withCodecRegistry(pojoCodecRegistry);
    }

    /**
     * Returns a Comment object that matches the provided id string.
     *
     * @param id - comment identifier
     * @return Comment object corresponding to the identifier value
     */
    public Comment getComment(String id) {
        return commentCollection.find(new Document("_id", new ObjectId(id))).first();
    }

    /**
     * Adds a new Comment to the collection. The equivalent instruction in the mongo shell would be:
     *
     * <p>db.comments.insertOne({comment})
     *
     * <p>
     *
     * @param comment - Comment object.
     * @throw IncorrectDaoOperation if the insert fails, otherwise
     * returns the resulting Comment object.
     */
    public Comment addComment(Comment comment) {

        if (comment.getId() == null) {
            throw new IncorrectDaoOperation("commentId is required");
        } else {
            commentCollection.insertOne(comment);
        }



        return comment;
    }

    /**
     * Updates the comment text matching commentId and user email. This method would be equivalent to
     * running the following mongo shell command:
     *
     * <p>db.comments.update({_id: commentId}, {$set: { "text": text, date: ISODate() }})
     *
     * <p>
     *
     * @param commentId - comment id string value.
     * @param text      - comment text to be updated.
     * @param email     - user email.
     * @return true if successfully updates the comment text.
     */
    public boolean updateComment(String commentId, String text, String email) {


        Bson idCommentFilter = Filters.eq("_id", new ObjectId(commentId));
        Bson emailFilter = Filters.eq("email", email);

        Comment comment = commentCollection.find(Filters.and(idCommentFilter, emailFilter))
                .first();

        if (comment != null) {
            commentCollection.updateOne(
                    Filters.and(idCommentFilter, emailFilter),
                    set("text", text));
            return true;
        }
        return false;


        // handle a potential write exception when given a wrong commentId.
    }

    /**
     * Deletes comment that matches user email and commentId.
     *
     * @param commentId - commentId string value.
     * @param email     - user email value.
     * @return true if successful deletes the comment.
     */
    public boolean deleteComment(String commentId, String email) {

        if (commentId == null || "".equals(commentId)) {
            throw new IllegalArgumentException("Incorrect commentId");
        }

        Bson filterQuery = Filters.and(
                Filters.eq("email", email),
                Filters.eq("_id", new ObjectId(commentId))
        );


        Comment comment = commentCollection.find(filterQuery).first();

        if (comment != null) {
            DeleteResult deleteResult = commentCollection.deleteOne(filterQuery);
            System.out.println(deleteResult.wasAcknowledged());
            return deleteResult.wasAcknowledged();
        }
        return false;
        // TIP: make sure to match only users that own the given commentId

        // handle a potential write exception when given a wrong commentId.

    }

    /**
     * Ticket: User Report - produce a list of users that comment the most in the website. Query the
     * `comments` collection and group the users by number of comments. The list is limited to up most
     * 20 commenter.
     *
     * @return List {@link Critic} objects.
     */
    public List<Critic> mostActiveCommenters() {
        List<Critic> mostActive = new ArrayList<>();

        MongoCollection<Critic> criticsCollection = db.getCollection(COMMENT_COLLECTION, Critic.class)
                .withCodecRegistry(pojoCodecRegistry).withReadConcern(ReadConcern.MAJORITY);

        List<Bson> pipeline = new ArrayList<>(Arrays.asList(
                new Document("$group",
                        new Document("_id", "$email")
                                .append("count",
                                        new Document("$count",
                                                new Document()))),
                new Document("$sort",
                        new Document("count", -1L)),
                new Document("$limit", 20L)));


        AggregateIterable<Critic> top20Critics = criticsCollection.aggregate(pipeline);

        for (Critic critic : top20Critics) {
            mostActive.add(critic);
        }

        return mostActive;
    }
}
