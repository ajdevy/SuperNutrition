CREATE TYPE "public"."activity_level" AS ENUM('sedentary', 'light', 'moderate', 'active', 'very_active');--> statement-breakpoint
CREATE TYPE "public"."auth_mode" AS ENUM('attested', 'test-mode');--> statement-breakpoint
CREATE TYPE "public"."auth_scope" AS ENUM('product', 'gateway', 'both');--> statement-breakpoint
CREATE TYPE "public"."catalog_package_status" AS ENUM('staged', 'active', 'rolled-back');--> statement-breakpoint
CREATE TYPE "public"."catalog_source" AS ENUM('usda', 'off');--> statement-breakpoint
CREATE TYPE "public"."coach_message_type" AS ENUM('motivation', 'recommendation', 'habit');--> statement-breakpoint
CREATE TYPE "public"."consent_decision" AS ENUM('granted', 'declined');--> statement-breakpoint
CREATE TYPE "public"."consent_modality" AS ENUM('photo', 'text');--> statement-breakpoint
CREATE TYPE "public"."evidence_type" AS ENUM('candidate', 'assumption', 'abstention', 'correction');--> statement-breakpoint
CREATE TYPE "public"."goal_type" AS ENUM('lose', 'maintain', 'gain', 'custom');--> statement-breakpoint
CREATE TYPE "public"."inference_outcome" AS ENUM('candidates', 'abstention', 'failure');--> statement-breakpoint
CREATE TYPE "public"."meal_source" AS ENUM('photo', 'manual', 'voice');--> statement-breakpoint
CREATE TYPE "public"."meal_status" AS ENUM('pending', 'identified', 'confirmed', 'failed');--> statement-breakpoint
CREATE TYPE "public"."meal_type" AS ENUM('breakfast', 'lunch', 'dinner', 'snack', 'unspecified');--> statement-breakpoint
CREATE TYPE "public"."measurement_system" AS ENUM('metric', 'imperial');--> statement-breakpoint
CREATE TYPE "public"."nutrition_origin" AS ENUM('client-grounded', 'server-grounded');--> statement-breakpoint
CREATE TYPE "public"."streak_type" AS ENUM('daily_logging', 'goal_adherence');--> statement-breakpoint
CREATE TYPE "public"."user_status" AS ENUM('active', 'suspended', 'deleted');--> statement-breakpoint
CREATE TABLE "user_profiles" (
	"user_id" uuid PRIMARY KEY NOT NULL,
	"display_name" text,
	"locale" text DEFAULT 'en-US' NOT NULL,
	"measurement_system" text DEFAULT 'metric' NOT NULL,
	"timezone" text DEFAULT 'UTC' NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"external_ref" text NOT NULL,
	"status" "user_status" DEFAULT 'active' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"deleted_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "sessions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"installation_id" text NOT NULL,
	"environment" text NOT NULL,
	"auth_mode" "auth_mode" NOT NULL,
	"scope" "auth_scope" NOT NULL,
	"refresh_token_hash" text NOT NULL,
	"refresh_family_id" uuid NOT NULL,
	"issued_at" timestamp with time zone DEFAULT now() NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"revoked_at" timestamp with time zone,
	"revoked_reason" text
);
--> statement-breakpoint
CREATE TABLE "consent_receipts" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"attempt_id" text NOT NULL,
	"purpose" text NOT NULL,
	"modality" "consent_modality" NOT NULL,
	"payload_digest" text NOT NULL,
	"market_jurisdiction" text NOT NULL,
	"processing_region" text NOT NULL,
	"retention_version" text NOT NULL,
	"configuration_version" text NOT NULL,
	"consent_copy_version" text NOT NULL,
	"decision" "consent_decision" NOT NULL,
	"decided_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_goals" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"goal_type" "goal_type" NOT NULL,
	"target_calories" integer NOT NULL,
	"target_protein_g" numeric(10, 3) NOT NULL,
	"target_fat_g" numeric(10, 3) NOT NULL,
	"target_carb_g" numeric(10, 3) NOT NULL,
	"activity_level" "activity_level" NOT NULL,
	"effective_from" date NOT NULL,
	"effective_to" date,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "meal_events" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"source" "meal_source" NOT NULL,
	"status" "meal_status" DEFAULT 'pending' NOT NULL,
	"occurred_at" timestamp with time zone NOT NULL,
	"local_date" date NOT NULL,
	"timezone" text NOT NULL,
	"meal_type" "meal_type" DEFAULT 'unspecified' NOT NULL,
	"revision" integer DEFAULT 1 NOT NULL,
	"supersedes_id" uuid,
	"attempt_id" text,
	"configuration_version" text,
	"transcript" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"confirmed_at" timestamp with time zone,
	"deleted_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "meal_items" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"meal_event_id" uuid NOT NULL,
	"position" integer NOT NULL,
	"food_ref" text NOT NULL,
	"display_name" text NOT NULL,
	"serving_unit" text NOT NULL,
	"serving_amount" numeric(10, 3) NOT NULL,
	"gram_weight" numeric(10, 3),
	"confidence" numeric(4, 3),
	"confirmed_by_user" boolean DEFAULT false NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "nutrition_snapshots" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"meal_event_id" uuid NOT NULL,
	"catalog_source" text NOT NULL,
	"dataset_version" text NOT NULL,
	"serving_basis" jsonb NOT NULL,
	"origin" "nutrition_origin" NOT NULL,
	"calories" numeric(10, 3) NOT NULL,
	"protein_g" numeric(10, 3),
	"fat_g" numeric(10, 3),
	"carb_g" numeric(10, 3),
	"fiber_g" numeric(10, 3),
	"sugar_g" numeric(10, 3),
	"sodium_mg" numeric(10, 3),
	"per_item" jsonb NOT NULL,
	"computed_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "inference_evidence" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"meal_event_id" uuid NOT NULL,
	"meal_item_id" uuid,
	"attempt_id" text NOT NULL,
	"type" "evidence_type" NOT NULL,
	"payload" jsonb NOT NULL,
	"provenance" jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "inference_runs" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"attempt_id" text NOT NULL,
	"pass_number" smallint NOT NULL,
	"provider" text NOT NULL,
	"model_id" text NOT NULL,
	"model_profile_version" text NOT NULL,
	"outcome" "inference_outcome" NOT NULL,
	"success" boolean NOT NULL,
	"confidence" numeric(4, 3),
	"duration_ms" integer NOT NULL,
	"duration_bucket" text NOT NULL,
	"prompt_tokens" integer,
	"completion_tokens" integer,
	"estimated_cost_micros" bigint,
	"failure_code" text,
	"market_jurisdiction" text,
	"processing_region" text,
	"environment" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "achievements" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"key" text NOT NULL,
	"name" text NOT NULL,
	"description" text NOT NULL,
	"icon_ref" text,
	"criteria" jsonb NOT NULL,
	"xp_reward" integer DEFAULT 0 NOT NULL,
	"active" boolean DEFAULT true NOT NULL,
	"sort_order" integer DEFAULT 0 NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_achievements" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"achievement_id" uuid NOT NULL,
	"unlocked_at" timestamp with time zone DEFAULT now() NOT NULL,
	"evidence" jsonb
);
--> statement-breakpoint
CREATE TABLE "streaks" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"type" "streak_type" NOT NULL,
	"current_count" integer DEFAULT 0 NOT NULL,
	"longest_count" integer DEFAULT 0 NOT NULL,
	"last_local_date" date,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "xp_transactions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"amount" integer NOT NULL,
	"reason" text NOT NULL,
	"ref_id" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "daily_progress" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"local_date" date NOT NULL,
	"total_calories" numeric(10, 3) NOT NULL,
	"total_protein_g" numeric(10, 3),
	"total_fat_g" numeric(10, 3),
	"total_carb_g" numeric(10, 3),
	"total_fiber_g" numeric(10, 3),
	"meal_count" integer DEFAULT 0 NOT NULL,
	"goal_id" uuid,
	"goal_adherence" jsonb,
	"computed_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "coach_messages" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"meal_event_id" uuid,
	"type" "coach_message_type" NOT NULL,
	"message_key" text NOT NULL,
	"params" jsonb,
	"locale" text,
	"delivered_at" timestamp with time zone,
	"read_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "catalog_packages" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"package_id" text NOT NULL,
	"catalog_country" text NOT NULL,
	"dataset_version" text NOT NULL,
	"source" "catalog_source" NOT NULL,
	"checksum" text NOT NULL,
	"license_id" text NOT NULL,
	"size_bytes" bigint,
	"status" "catalog_package_status" DEFAULT 'staged' NOT NULL,
	"activated_at" timestamp with time zone,
	"rollback_target_id" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "catalog_items" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"package_id" uuid NOT NULL,
	"food_id" text NOT NULL,
	"display_name" text NOT NULL,
	"nutrients_per_100g" jsonb NOT NULL,
	"serving_options" jsonb NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "user_profiles" ADD CONSTRAINT "user_profiles_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "sessions" ADD CONSTRAINT "sessions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "consent_receipts" ADD CONSTRAINT "consent_receipts_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_goals" ADD CONSTRAINT "user_goals_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "meal_events" ADD CONSTRAINT "meal_events_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "meal_events" ADD CONSTRAINT "meal_events_supersedes_id_meal_events_id_fk" FOREIGN KEY ("supersedes_id") REFERENCES "public"."meal_events"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "meal_items" ADD CONSTRAINT "meal_items_meal_event_id_meal_events_id_fk" FOREIGN KEY ("meal_event_id") REFERENCES "public"."meal_events"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "nutrition_snapshots" ADD CONSTRAINT "nutrition_snapshots_meal_event_id_meal_events_id_fk" FOREIGN KEY ("meal_event_id") REFERENCES "public"."meal_events"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "inference_evidence" ADD CONSTRAINT "inference_evidence_meal_event_id_meal_events_id_fk" FOREIGN KEY ("meal_event_id") REFERENCES "public"."meal_events"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "inference_evidence" ADD CONSTRAINT "inference_evidence_meal_item_id_meal_items_id_fk" FOREIGN KEY ("meal_item_id") REFERENCES "public"."meal_items"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_achievements" ADD CONSTRAINT "user_achievements_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_achievements" ADD CONSTRAINT "user_achievements_achievement_id_achievements_id_fk" FOREIGN KEY ("achievement_id") REFERENCES "public"."achievements"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "streaks" ADD CONSTRAINT "streaks_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "xp_transactions" ADD CONSTRAINT "xp_transactions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "daily_progress" ADD CONSTRAINT "daily_progress_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "daily_progress" ADD CONSTRAINT "daily_progress_goal_id_user_goals_id_fk" FOREIGN KEY ("goal_id") REFERENCES "public"."user_goals"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "coach_messages" ADD CONSTRAINT "coach_messages_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "coach_messages" ADD CONSTRAINT "coach_messages_meal_event_id_meal_events_id_fk" FOREIGN KEY ("meal_event_id") REFERENCES "public"."meal_events"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "catalog_packages" ADD CONSTRAINT "catalog_packages_rollback_target_id_catalog_packages_id_fk" FOREIGN KEY ("rollback_target_id") REFERENCES "public"."catalog_packages"("id") ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "catalog_items" ADD CONSTRAINT "catalog_items_package_id_catalog_packages_id_fk" FOREIGN KEY ("package_id") REFERENCES "public"."catalog_packages"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "users_external_ref_uq" ON "users" USING btree ("external_ref");--> statement-breakpoint
CREATE INDEX "users_status_idx" ON "users" USING btree ("status");--> statement-breakpoint
CREATE INDEX "sessions_refresh_hash_idx" ON "sessions" USING btree ("refresh_token_hash");--> statement-breakpoint
CREATE INDEX "sessions_family_idx" ON "sessions" USING btree ("refresh_family_id");--> statement-breakpoint
CREATE INDEX "sessions_user_active_idx" ON "sessions" USING btree ("user_id","revoked_at");--> statement-breakpoint
CREATE INDEX "consent_receipts_user_idx" ON "consent_receipts" USING btree ("user_id","decided_at");--> statement-breakpoint
CREATE INDEX "consent_receipts_digest_idx" ON "consent_receipts" USING btree ("payload_digest");--> statement-breakpoint
CREATE INDEX "user_goals_user_effective_idx" ON "user_goals" USING btree ("user_id","effective_from");--> statement-breakpoint
CREATE INDEX "meal_events_user_occurred_idx" ON "meal_events" USING btree ("user_id","occurred_at");--> statement-breakpoint
CREATE INDEX "meal_events_user_local_date_idx" ON "meal_events" USING btree ("user_id","local_date");--> statement-breakpoint
CREATE INDEX "meal_events_user_status_idx" ON "meal_events" USING btree ("user_id","status");--> statement-breakpoint
CREATE INDEX "meal_events_attempt_idx" ON "meal_events" USING btree ("attempt_id");--> statement-breakpoint
CREATE INDEX "meal_items_event_position_idx" ON "meal_items" USING btree ("meal_event_id","position");--> statement-breakpoint
CREATE INDEX "meal_items_food_ref_idx" ON "meal_items" USING btree ("food_ref");--> statement-breakpoint
CREATE UNIQUE INDEX "nutrition_snapshots_meal_event_uq" ON "nutrition_snapshots" USING btree ("meal_event_id");--> statement-breakpoint
CREATE INDEX "inference_evidence_meal_idx" ON "inference_evidence" USING btree ("meal_event_id");--> statement-breakpoint
CREATE INDEX "inference_evidence_attempt_idx" ON "inference_evidence" USING btree ("attempt_id");--> statement-breakpoint
CREATE INDEX "inference_runs_created_idx" ON "inference_runs" USING btree ("created_at");--> statement-breakpoint
CREATE INDEX "inference_runs_model_created_idx" ON "inference_runs" USING btree ("model_id","created_at");--> statement-breakpoint
CREATE UNIQUE INDEX "achievements_key_uq" ON "achievements" USING btree ("key");--> statement-breakpoint
CREATE UNIQUE INDEX "user_achievements_uq" ON "user_achievements" USING btree ("user_id","achievement_id");--> statement-breakpoint
CREATE UNIQUE INDEX "streaks_user_type_uq" ON "streaks" USING btree ("user_id","type");--> statement-breakpoint
CREATE UNIQUE INDEX "xp_transactions_idempotency_uq" ON "xp_transactions" USING btree ("user_id","reason","ref_id");--> statement-breakpoint
CREATE INDEX "xp_transactions_user_created_idx" ON "xp_transactions" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE UNIQUE INDEX "daily_progress_user_date_uq" ON "daily_progress" USING btree ("user_id","local_date");--> statement-breakpoint
CREATE INDEX "daily_progress_user_date_desc_idx" ON "daily_progress" USING btree ("user_id","local_date");--> statement-breakpoint
CREATE INDEX "coach_messages_user_created_idx" ON "coach_messages" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE UNIQUE INDEX "catalog_packages_package_id_uq" ON "catalog_packages" USING btree ("package_id");--> statement-breakpoint
CREATE UNIQUE INDEX "catalog_items_package_food_uq" ON "catalog_items" USING btree ("package_id","food_id");