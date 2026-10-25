ALTER TABLE content ADD COLUMN kind TEXT NOT NULL DEFAULT 'link' CHECK(kind IN ('link','collection'));
-- Published Dulo navigation/genre URLs, checked 2026-10-25. Existing admin entries are preserved.
INSERT INTO content(id,title,category,url,kind) VALUES
 ('dulo-library-movies','Movies','Browse libraries','https://dulo.mov/movies/','collection'),
 ('dulo-library-shows','TV Shows','Browse libraries','https://dulo.mov/shows/','collection'),
 ('dulo-library-cartoons','Cartoons & Animation','Browse libraries','https://dulo.mov/shows/?shelf=genre-16','collection'),
 ('dulo-library-animated-movies','Animated Movies','Browse libraries','https://dulo.mov/movies/?shelf=genre-16','collection'),
 ('dulo-library-anime','Anime','Browse libraries','https://dulo.mov/anime/','collection'),
 ('dulo-library-family','Family Movies','Browse libraries','https://dulo.mov/movies/?shelf=genre-10751','collection'),
 ('dulo-library-documentaries','Documentaries','Browse libraries','https://dulo.mov/movies/?shelf=genre-99','collection'),
 ('dulo-library-action','Action & Adventure','Browse libraries','https://dulo.mov/movies/?shelf=genre-28','collection'),
 ('dulo-library-comedy','Comedy','Browse libraries','https://dulo.mov/movies/?shelf=genre-35','collection'),
 ('dulo-library-live','Live TV','Browse libraries','https://dulo.mov/live','collection');
