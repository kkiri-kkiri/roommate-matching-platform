START TRANSACTION READ ONLY;
SELECT DATABASE() schema_name,VERSION() mysql_version;
SELECT 'counts' section,
 (SELECT COUNT(*) FROM user) users,
 (SELECT COUNT(*) FROM user_profile) profiles,
 (SELECT COUNT(*) FROM profile_survey) surveys,
 (SELECT COUNT(*) FROM roommate_preference) preferences,
 (SELECT COUNT(*) FROM preference_criterion) weights,
 (SELECT COUNT(*) FROM community_post) posts,
 (SELECT COUNT(*) FROM community_comment) comments,
 (SELECT COUNT(*) FROM post_like) post_likes,
 (SELECT COUNT(*) FROM comment_like) comment_likes,
 (SELECT COUNT(*) FROM chat_room) chat_rooms,
 (SELECT COUNT(*) FROM chat_room_member) chat_members,
 (SELECT COUNT(*) FROM chat_message) messages,
 (SELECT COUNT(*) FROM calendar_event) events,
 (SELECT COUNT(*) FROM report) reports;
SELECT category,COUNT(*) posts,SUM(title LIKE '[SEED] %') old_titles FROM community_post GROUP BY category;
SELECT status,COUNT(*) reports FROM report GROUP BY status;
SELECT source_type,COUNT(*) events FROM calendar_event GROUP BY source_type;
SELECT COUNT(*) seed_users,SUM(p.nickname REGEXP '^테스트룸메[0-9]{2}$') old_nicknames,COUNT(DISTINCT p.nickname) distinct_nicknames FROM user u JOIN user_profile p ON p.user_id=u.user_id WHERE u.school_email REGEXP '^seed_user(0[1-9]|[12][0-9]|30)@hknu[.]ac[.]kr$';
SELECT COUNT(*) matching_rows,MIN(match_score) minimum_score,MAX(match_score) maximum_score FROM roommate_match;
SELECT COUNT(*) orphan_comments FROM community_comment c LEFT JOIN community_post p ON p.post_id=c.post_id WHERE p.post_id IS NULL;
SELECT COUNT(*) orphan_messages FROM chat_message m LEFT JOIN chat_room r ON r.chat_room_id=m.chat_room_id WHERE r.chat_room_id IS NULL;
ROLLBACK;
