"""Independent pinhole oracle for the graph's half-pixel marking-noise fixture."""
from math import asin, cos, degrees, radians, sin, sqrt


def add(a, b):
    return [x + y for x, y in zip(a, b)]


def sub(a, b):
    return [x - y for x, y in zip(a, b)]


def mul(a, value):
    return [x * value for x in a]


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def norm(a):
    return sqrt(dot(a, a))


def unit(a):
    return mul(a, 1 / norm(a))


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def rotate(p):
    yaw, pitch = map(radians, [30, 20])
    x, y, z = p
    x, z = cos(yaw) * x + sin(yaw) * z, -sin(yaw) * x + cos(yaw) * z
    return [x, cos(pitch) * y - sin(pitch) * z, sin(pitch) * y + cos(pitch) * z]


def project(width, height):
    points = []
    for x, y in [(-1, -1), (1, -1), (1, 1), (-1, 1)]:
        a, b, c = add(rotate([x * width / 2, y * height / 2, 0]), [0, 0, 4])
        points.append([1000 * a / c + 1000, 1000 * b / c + 750])
    return points


def back(p):
    return [(p[0] - 1000 * p[2]) / 1000, (p[1] - 750 * p[2]) / 1000, p[2]]


def mean_edges(p, a, b, c, d):
    return (norm(sub(p[a], p[b])) + norm(sub(p[c], p[d]))) / 2


def check_fixture():
    base, stick = project(2, 1), project(1, .04)
    offsets = [[.5, 0], [0, -.5], [-.5, 0], [0, .5]]
    results = []
    for sign in [1, -1]:
        points = [add(a, mul(b, sign)) + [1] for a, b in zip(base, offsets)]
        x = unit(back(cross(cross(points[0], points[1]), cross(points[3], points[2]))))
        y = unit(back(cross(cross(points[0], points[3]), cross(points[1], points[2]))))
        normal = unit(cross(x, y))

        def recover(quad):
            return [mul(ray, 1 / dot(ray, normal)) for ray in map(back, quad)]

        target, reference = recover(points), recover([p + [1] for p in stick])
        scale = 1 / mean_edges(reference, 0, 1, 2, 3)
        width = mean_edges(target, 0, 1, 2, 3) * scale
        height = mean_edges(target, 0, 3, 1, 2) * scale
        row = dict(offset_sign=sign, width_relative_error=abs(width / 2 - 1),
                   height_relative_error=abs(height - 1),
                   wall_consistency_degrees=degrees(asin(abs(dot(normal, rotate([0, 1, 0]))))))
        if not (row['width_relative_error'] < .02 and row['height_relative_error'] < .02
                and row['wall_consistency_degrees'] < 5):
            raise ValueError(f"Independent marking-noise fixture failed: {row}")
        results.append(row)
    return results
