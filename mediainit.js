// =============================
// MongoDB Media Database Init
// Database: media
// =============================

use("media");

// -----------------------------
// Create Collections
// -----------------------------
const collections = [
    "videos",
    "episodes",
    "video_sources",
    "persons",
    "categories",
    "users",
    "favorites",
    "watch_history",
    "comments",
    "search_logs"
];

collections.forEach(name => {
    if (!db.getCollectionNames().includes(name)) {
        db.createCollection(name);
        print(`Created collection: ${name}`);
    } else {
        print(`Collection already exists: ${name}`);
    }
});

// =============================
// videos
// =============================

db.videos.createIndex(
    { category: 1, createdAt: -1 },
    { name: "idx_category_createdAt" }
);

db.videos.createIndex(
    { category: 1, genres: 1 },
    { name: "idx_category_genres" }
);

db.videos.createIndex(
    { "release.year": -1 },
    { name: "idx_release_year" }
);

db.videos.createIndex(
    { "flags.hot": 1, "stats.views": -1 },
    { name: "idx_hot_views" }
);

db.videos.createIndex(
    { "flags.recommend": 1, "rating.score": -1 },
    { name: "idx_recommend_rating" }
);

db.videos.createIndex(
    {
        title: "text",
        originalTitle: "text",
        tags: "text"
    },
    {
        name: "idx_text_search"
    }
);

db.videos.createIndex(
    {
        "externalIds.tmdb": 1
    },
    {
        unique: true,
        sparse: true,
        name: "idx_tmdb"
    }
);

// =============================
// episodes
// =============================

db.episodes.createIndex(
    {
        videoId: 1,
        season: 1,
        episode: 1
    },
    {
        unique: true,
        name: "idx_episode_unique"
    }
);

db.episodes.createIndex(
    {
        videoId: 1,
        episode: 1
    },
    {
        name: "idx_video_episode"
    }
);

// =============================
// video_sources
// =============================

db.video_sources.createIndex(
    {
        videoId: 1,
        quality: 1
    },
    {
        name: "idx_video_quality"
    }
);

db.video_sources.createIndex(
    {
        episodeId: 1,
        status: 1
    },
    {
        name: "idx_episode_status"
    }
);

// =============================
// persons
// =============================

db.persons.createIndex(
    {
        name: "text"
    },
    {
        name: "idx_person_text"
    }
);

db.persons.createIndex(
    {
        name: 1,
        birthday: 1
    },
    {
        name: "idx_person_unique"
    }
);

// =============================
// categories
// =============================

db.categories.createIndex(
    {
        sort: 1
    },
    {
        name: "idx_category_sort"
    }
);

// =============================
// users
// =============================

db.users.createIndex(
    {
        email: 1
    },
    {
        unique: true,
        name: "idx_email_unique"
    }
);

db.users.createIndex(
    {
        username: 1
    },
    {
        unique: true,
        sparse: true,
        name: "idx_username"
    }
);

// =============================
// favorites
// =============================

db.favorites.createIndex(
    {
        userId: 1,
        videoId: 1
    },
    {
        unique: true,
        name: "idx_user_video"
    }
);

db.favorites.createIndex(
    {
        userId: 1,
        createdAt: -1
    },
    {
        name: "idx_user_favorites"
    }
);

// =============================
// watch_history
// =============================

db.watch_history.createIndex(
    {
        userId: 1,
        updatedAt: -1
    },
    {
        name: "idx_watch_history"
    }
);

// =============================
// comments
// =============================

db.comments.createIndex(
    {
        videoId: 1,
        createdAt: -1
    },
    {
        name: "idx_comments"
    }
);

// =============================
// search_logs
// =============================

db.search_logs.createIndex(
    {
        createdAt: 1
    },
    {
        expireAfterSeconds: 60 * 60 * 24 * 30,
        name: "idx_search_ttl"
    }
);

db.search_logs.createIndex(
    {
        keyword: 1,
        createdAt: -1
    },
    {
        name: "idx_keyword"
    }
);

print("");
print("==============================");
print(" Media Database Initialized");
print("==============================");
print("Collections:");
printjson(db.getCollectionNames());
print("==============================");
