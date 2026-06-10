/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <cmath>
#include <memory>
#include <vector>

#include "AndroidOut.h"
#include "TextureAsset.h"

union Vector3 {
    struct {
        float x, y, z;
    };
    float idx[3];
    Vector3 operator*(float value) { return Vector3{{x * value, y * value, z * value}}; }
    Vector3 operator/(float value) { return Vector3{{x / value, y / value, z / value}}; }
    Vector3 operator+(Vector3 const &other) {
        return Vector3{{x + other.x, y + other.y, z + other.z}};
    }
    Vector3 operator-(Vector3 const &other) {
        return Vector3{{x - other.x, y - other.y, z - other.z}};
    }
};

union Vector2 {
    struct {
        float x, y;
    };
    struct {
        float u, v;
    };
    float idx[2];
};

struct Vertex {
    constexpr Vertex(const Vector3 &inPosition, const Vector2 &inUV)
          : position(inPosition), uv(inUV) {}

    Vector3 position;
    Vector2 uv;
};

typedef uint16_t Index;

class Model {
public:
    static std::shared_ptr<TextureAsset> texture;
    // Default init only useful as a placeholder
    Model() {}

    inline Model(std::vector<Vertex> vertices, std::vector<Index> indices)
          : currentVertices_(vertices),
            startVertices_(std::move(vertices)),
            indices_(std::move(indices)),
            id_(0) {
        findCenter();
    }

    inline const Vertex *getVertexData() const { return currentVertices_.data(); }

    inline size_t getIndexCount() const { return indices_.size(); }

    inline const Index *getIndexData() const { return indices_.data(); }

    inline const TextureAsset &getTexture() const { return *texture; }

    inline const Vector3 getCenter() { return center_; }

    inline void dump() const {
        aout << "Indices: " << std::endl;
        for (auto &&ver : currentVertices_) {
            aout << "Vertex: x: " << ver.position.x << " y: " << ver.position.y
                 << " z: " << ver.position.z << std::endl;
        }
        aout << std::endl;
        aout << "Center: x: " << center_.x << " y: " << center_.y << " z: " << center_.z
             << std::endl;
    }

    void move(Vector3 offset) {
        for (int i = 0; i < startVertices_.size(); ++i) {
            startVertices_[i].position = startVertices_[i].position + offset;
            currentVertices_[i].position = currentVertices_[i].position + offset;
        }
        center_ = center_ + offset;
    }

    void addRotation(float angle) {
        float rad = angle + rotationOffset_;
        for (int i = 0; i < startVertices_.size(); ++i) {
            Vector3 normalized = startVertices_[i].position - center_;
            Vector3 out{{0, 0, 0}};
            out.x = normalized.x * cos(rad) - normalized.y * sin(rad);
            out.y = normalized.x * sin(rad) + normalized.y * cos(rad);
            currentVertices_[i].position = out + center_;
        }
        rotationOffset_ = rad;
    }

    void setRotationOffset(float angle) { rotationOffset_ = angle; }

    static void applyPhysics(float deltaTimeUnit, Model *models, int size, float width,
                             float height) {
        nBodySimulation(deltaTimeUnit, models, size, width, height);
    }

    void setMass(float m) { mass_ = m; }

    int getId() const { return id_; }
    void setId(int id) { id_ = id; }

private:
    void findCenter() {
        Vector3 center{{0, 0, 0}};
        for (auto &&vertex : startVertices_) {
            center = center + vertex.position;
        }
        center_ = center / static_cast<float>(startVertices_.size());
    }

    static void nBodySimulation(float deltaTimeUnit, Model *models, int size, float width,
                                float height) {
        static const float G = 6.67e-10;
        for (auto i = 0; i < size; i++) {
            auto &model = models[i];
            Vector3 acc = {{0, 0, 0}};
            for (auto j = 0; j < size; j++) {
                if (i != j) {
                    auto &other = models[j];
                    auto dx = model.center_.x - other.center_.x;
                    auto dy = model.center_.y - other.center_.y;
                    auto dz = model.center_.z - other.center_.z;
                    float distanceSq = dx * dx + dy * dy + dz * dz;
                    Vector3 direction = {{dx, dy, dz}};
                    float distance = std::sqrt(distanceSq);
                    float force = (G * model.mass_ * other.mass_) / std::max(0.01f, distanceSq);
                    acc = acc + (direction / std::max(0.01f, distance)) * (force / model.mass_);
                }
            }
            model.velocity_ = model.velocity_ + acc * deltaTimeUnit;
            model.move(model.velocity_ * deltaTimeUnit);
            if (model.center_.x <= -width / 2 || model.center_.x >= width / 2) {
                model.velocity_.x = model.center_.x <= -width / 2 ? abs(model.velocity_.x)
                                                                  : -abs(model.velocity_.x);
                auto border = model.center_.x <= -width / 2 ? -width / 2 : width / 2;
                model.move({{border - model.center_.x, 0, 0}});
            }
            if (model.center_.y <= -height / 2 || model.center_.y >= height / 2) {
                model.velocity_.y = model.center_.y <= -height / 2 ? abs(model.velocity_.y)
                                                                   : -abs(model.velocity_.y);
                auto border = model.center_.y <= -height / 2 ? -height / 2 : height / 2;
                model.move({{0, border - model.center_.y, 0}});
            }
        }
    }

    Vector3 center_;
    std::vector<Vertex> currentVertices_;
    std::vector<Vertex> startVertices_;
    std::vector<Index> indices_;
    float rotationOffset_;
    Vector3 velocity_;
    float mass_ = 1.0f;
    int id_;
};
